package com.marianocarrizo.dynamusic.audio.cache;

import com.marianocarrizo.dynamusic.audio.AudioFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class AudioCache {

    public record CachedAudio(Optional<Path> wav, double gain) {
    }

    @FunctionalInterface
    public interface FileDeleter {
        boolean delete(Path file);
    }

    static final String INDEX_FILE = "index.tsv";
    static final String WAV_SUFFIX = "." + AudioFormat.WAV.primaryExtension();
    static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path directory;
    private final LongSupplier clock;
    private final FileDeleter deleter;
    private final Consumer<Runnable> flushScheduler;
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>();
    private long limitBytes;
    private boolean flushScheduled;

    public AudioCache(Path directory, CacheLimit limit, LongSupplier clock, FileDeleter deleter,
                      Consumer<Runnable> flushScheduler) {
        this(directory, limit.bytes(), clock, deleter, flushScheduler);
    }

    AudioCache(Path directory, long limitBytes, LongSupplier clock, FileDeleter deleter,
               Consumer<Runnable> flushScheduler) {
        this.directory = directory;
        this.limitBytes = limitBytes;
        this.clock = clock;
        this.deleter = deleter;
        this.flushScheduler = flushScheduler;
    }

    public static FileDeleter filesystemDeleter() {
        return file -> {
            try {
                Files.deleteIfExists(file);
                return true;
            } catch (IOException | RuntimeException locked) {
                return false;
            }
        };
    }

    public synchronized void initialize() {
        try {
            Files.createDirectories(directory);
            deleteTemporaryFiles();
            loadIndex();
            dropEntriesWithoutFiles();
            deleteFilesWithoutEntries();
            evict(Set.of());
            flush();
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("[audio-cache] initialization failed: " + unreadable);
        }
    }

    public synchronized Optional<CachedAudio> lookup(CacheKey key) {
        CacheEntry entry = entries.get(key.hex());
        if (entry == null) return Optional.empty();
        if (entry.hasWav() && !Files.isRegularFile(wavPath(key.hex()))) {
            entries.remove(key.hex());
            markDirty();
            return Optional.empty();
        }
        entries.put(key.hex(), entry.touched(clock.getAsLong()));
        markDirty();
        Optional<Path> wav = entry.hasWav() ? Optional.of(wavPath(key.hex())) : Optional.empty();
        return Optional.of(new CachedAudio(wav, entry.gain()));
    }

    public Path temporaryFile(CacheKey key) throws IOException {
        Files.createDirectories(directory);
        return directory.resolve(key.hex() + "." + UUID.randomUUID() + TEMPORARY_SUFFIX);
    }

    public synchronized Path storeConverted(CacheKey key, Path temporary, double gain) throws IOException {
        Path wav = wavPath(key.hex());
        try {
            move(temporary, wav);
            entries.put(key.hex(), new CacheEntry(key.hex(), gain, Files.size(wav), clock.getAsLong()));
        } catch (IOException failure) {
            deleter.delete(temporary);
            throw failure;
        }
        evict(Set.of(key.hex()));
        markDirty();
        return wav;
    }

    public synchronized void storeGain(CacheKey key, double gain) {
        entries.put(key.hex(), new CacheEntry(key.hex(), gain, 0, clock.getAsLong()));
        entries.keySet().removeAll(LruPlan.gainOnlyVictims(entries.values()));
        markDirty();
    }

    public synchronized void setLimit(CacheLimit newLimit) {
        setLimitBytes(newLimit.bytes());
    }

    synchronized void setLimitBytes(long newLimitBytes) {
        limitBytes = newLimitBytes;
        evict(Set.of());
        markDirty();
    }

    public synchronized long usedBytes() {
        return entries.values().stream().mapToLong(CacheEntry::bytes).sum();
    }

    public synchronized void clear() {
        for (CacheEntry entry : List.copyOf(entries.values())) {
            if (!entry.hasWav() || deleter.delete(wavPath(entry.key()))) entries.remove(entry.key());
        }
        markDirty();
    }

    public synchronized void flush() {
        flushScheduled = false;
        Path temporary = directory.resolve(INDEX_FILE + TEMPORARY_SUFFIX);
        try {
            Files.createDirectories(directory);
            Files.writeString(temporary, CacheIndexCodec.encode(entries.values()), StandardCharsets.UTF_8);
            move(temporary, directory.resolve(INDEX_FILE));
        } catch (IOException | RuntimeException failure) {
            System.err.println("[audio-cache] could not write the index: " + failure);
        }
    }

    Path wavPath(String keyHex) {
        return directory.resolve(keyHex + WAV_SUFFIX);
    }

    private void markDirty() {
        if (flushScheduled) return;
        flushScheduled = true;
        flushScheduler.accept(this::flush);
    }

    private void evict(Set<String> protectedKeys) {
        Set<String> skipped = new HashSet<>(protectedKeys);
        List<String> victims = LruPlan.wavVictims(entries.values(), limitBytes, skipped);
        while (!victims.isEmpty()) {
            for (String victim : victims) {
                if (deleter.delete(wavPath(victim))) {
                    entries.remove(victim);
                } else {
                    skipped.add(victim);
                }
            }
            victims = LruPlan.wavVictims(entries.values(), limitBytes, skipped);
        }
        entries.keySet().removeAll(LruPlan.gainOnlyVictims(entries.values()));
    }

    private void deleteTemporaryFiles() throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + TEMPORARY_SUFFIX)) {
            for (Path file : files) deleter.delete(file);
        }
    }

    private void loadIndex() {
        Path index = directory.resolve(INDEX_FILE);
        try {
            String text = Files.isRegularFile(index) ? Files.readString(index, StandardCharsets.UTF_8) : "";
            for (CacheEntry entry : CacheIndexCodec.decode(text)) entries.put(entry.key(), entry);
        } catch (IOException | RuntimeException corrupt) {
            entries.clear();
        }
    }

    private void dropEntriesWithoutFiles() {
        entries.values().removeIf(entry -> entry.hasWav() && !Files.isRegularFile(wavPath(entry.key())));
    }

    private void deleteFilesWithoutEntries() throws IOException {
        try (DirectoryStream<Path> wavs = Files.newDirectoryStream(directory, "*" + WAV_SUFFIX)) {
            for (Path wav : wavs) {
                String name = wav.getFileName().toString();
                String key = name.substring(0, name.length() - WAV_SUFFIX.length());
                CacheEntry entry = entries.get(key);
                if (entry == null || !entry.hasWav()) deleter.delete(wav);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
