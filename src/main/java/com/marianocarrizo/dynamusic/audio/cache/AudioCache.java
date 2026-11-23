package com.marianocarrizo.dynamusic.audio.cache;

import com.marianocarrizo.dynamusic.audio.AudioFormat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

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
    static final long READY_TIMEOUT_SECONDS = 20;

    private final Path directory;
    private final LongSupplier clock;
    private final FileDeleter deleter;
    private final Consumer<Runnable> flushScheduler;
    private final CountDownLatch ready = new CountDownLatch(1);
    private final Map<String, CacheEntry> entries = new LinkedHashMap<>();
    private final Map<String, Integer> pins = new HashMap<>();
    private long limitBytes;
    private boolean flushScheduled;
    private boolean indexTrusted = true;

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

    public void initialize(Supplier<CacheLimit> savedLimit) {
        CacheLimit saved = savedLimit.get();
        synchronized (this) {
            if (saved != null) limitBytes = saved.bytes();
        }
        initialize();
    }

    public void initialize() {
        try {
            synchronized (this) {
                loadAndCleanUp();
            }
            evict(Set.of());
            synchronized (this) {
                flush();
            }
        } finally {
            ready.countDown();
        }
    }

    public Optional<CachedAudio> lookup(CacheKey key) {
        awaitReady();
        synchronized (this) {
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
    }

    public Path temporaryFile(CacheKey key) throws IOException {
        awaitReady();
        Files.createDirectories(directory);
        return directory.resolve(key.hex() + "." + UUID.randomUUID() + TEMPORARY_SUFFIX);
    }

    public Path storeConverted(CacheKey key, Path temporary, double gain) throws IOException {
        awaitReady();
        Path wav = wavPath(key.hex());
        synchronized (this) {
            try {
                move(temporary, wav);
                long size = sizeOrDelete(wav);
                entries.put(key.hex(), new CacheEntry(key.hex(), gain, size, clock.getAsLong()));
            } catch (IOException failure) {
                deleter.delete(temporary);
                throw failure;
            }
            markDirty();
        }
        evict(Set.of(key.hex()));
        return wav;
    }

    public synchronized void storeGain(CacheKey key, double gain) {
        entries.put(key.hex(), new CacheEntry(key.hex(), gain, 0, clock.getAsLong()));
        entries.keySet().removeAll(LruPlan.gainOnlyVictims(entries.values()));
        markDirty();
    }

    public void setLimit(CacheLimit newLimit) {
        setLimitBytes(newLimit.bytes());
    }

    void setLimitBytes(long newLimitBytes) {
        synchronized (this) {
            limitBytes = newLimitBytes;
            markDirty();
        }
        evict(Set.of());
    }

    public synchronized long usedBytes() {
        return entries.values().stream().mapToLong(CacheEntry::bytes).sum();
    }

    public void clear() {
        List<CacheEntry> removed = new ArrayList<>();
        synchronized (this) {
            for (CacheEntry entry : List.copyOf(entries.values())) {
                if (isPinned(entry.key())) continue;
                entries.remove(entry.key());
                if (entry.hasWav()) removed.add(entry);
            }
            markDirty();
        }
        restoreUndeleted(deleteAll(removed));
    }

    public synchronized void pin(File wav) {
        cacheKeyOf(wav).ifPresent(key -> pins.merge(key, 1, Integer::sum));
    }

    public synchronized void unpin(File wav) {
        cacheKeyOf(wav).ifPresent(key -> pins.computeIfPresent(key, (name, count) -> count > 1 ? count - 1 : null));
    }

    public synchronized void flush() {
        flushScheduled = false;
        if (!indexTrusted) retryIndexRead();
        if (!indexTrusted) return;
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

    private void awaitReady() {
        try {
            ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void markDirty() {
        if (flushScheduled) return;
        flushScheduled = true;
        flushScheduler.accept(this::flush);
    }

    private boolean isPinned(String keyHex) {
        return pins.containsKey(keyHex);
    }

    private Optional<String> cacheKeyOf(File wav) {
        if (wav == null || !directory.equals(wav.toPath().getParent())) return Optional.empty();
        String name = wav.getName();
        if (!name.endsWith(WAV_SUFFIX)) return Optional.empty();
        return Optional.of(name.substring(0, name.length() - WAV_SUFFIX.length()));
    }

    private long sizeOrDelete(Path wav) throws IOException {
        try {
            return Files.size(wav);
        } catch (IOException unreadable) {
            deleter.delete(wav);
            throw unreadable;
        }
    }

    private void evict(Set<String> protectedKeys) {
        Set<String> skipped = new HashSet<>(protectedKeys);
        List<CacheEntry> victims = takeVictims(skipped);
        while (!victims.isEmpty()) {
            List<CacheEntry> undeleted = deleteAll(victims);
            synchronized (this) {
                for (CacheEntry entry : undeleted) skipped.add(entry.key());
            }
            restoreUndeleted(undeleted);
            victims = takeVictims(skipped);
        }
        synchronized (this) {
            entries.keySet().removeAll(LruPlan.gainOnlyVictims(entries.values()));
        }
    }

    private synchronized List<CacheEntry> takeVictims(Set<String> skipped) {
        Set<String> excluded = new HashSet<>(skipped);
        excluded.addAll(pins.keySet());
        List<CacheEntry> victims = new ArrayList<>();
        for (String key : LruPlan.wavVictims(entries.values(), limitBytes, excluded)) {
            victims.add(entries.remove(key));
        }
        if (!victims.isEmpty()) markDirty();
        return victims;
    }

    private List<CacheEntry> deleteAll(List<CacheEntry> victims) {
        List<CacheEntry> undeleted = new ArrayList<>();
        for (CacheEntry victim : victims) {
            if (!deleter.delete(wavPath(victim.key()))) undeleted.add(victim);
        }
        return undeleted;
    }

    private synchronized void restoreUndeleted(List<CacheEntry> undeleted) {
        for (CacheEntry entry : undeleted) entries.putIfAbsent(entry.key(), entry);
    }

    private void loadAndCleanUp() {
        try {
            Files.createDirectories(directory);
            deleteTemporaryFiles();
            indexTrusted = readIndex();
            if (!indexTrusted) return;
            dropEntriesWithoutFiles();
            deleteFilesWithoutEntries();
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("[audio-cache] initialization failed: " + unreadable);
            indexTrusted = false;
        }
    }

    private void retryIndexRead() {
        Map<String, CacheEntry> inMemory = new LinkedHashMap<>(entries);
        entries.clear();
        if (readIndex()) {
            inMemory.forEach(entries::put);
            indexTrusted = true;
            dropEntriesWithoutFiles();
        } else {
            entries.putAll(inMemory);
        }
    }

    private boolean readIndex() {
        Path index = directory.resolve(INDEX_FILE);
        if (!Files.exists(index)) return true;
        try {
            String text = Files.readString(index, StandardCharsets.UTF_8);
            if (!text.isEmpty() && !CacheIndexCodec.hasValidHeader(text)) return false;
            for (CacheEntry entry : CacheIndexCodec.decode(text)) entries.put(entry.key(), entry);
            return true;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private void deleteTemporaryFiles() throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + TEMPORARY_SUFFIX)) {
            for (Path file : files) deleter.delete(file);
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
