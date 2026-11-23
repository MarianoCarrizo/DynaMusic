package com.marianocarrizo.dynamusic.audio.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioCacheTest {

    private static final long UNIT = 1024;

    @TempDir Path dir;

    private final AtomicLong clock = new AtomicLong(1);
    private final Set<Path> undeletable = new HashSet<>();
    private int scheduledFlushes;

    private AudioCache cache(long limitBytes) {
        AudioCache.FileDeleter deleter = file -> !undeletable.contains(file) && AudioCache.filesystemDeleter().delete(file);
        AudioCache cache = new AudioCache(dir, limitBytes, clock::incrementAndGet, deleter, flush -> scheduledFlushes++);
        cache.initialize();
        return cache;
    }

    private static CacheKey key(String name) {
        return CacheKey.of(new File(name), 1, 1);
    }

    private Path store(AudioCache cache, CacheKey key, long bytes, double gain) throws IOException {
        Path temporary = cache.temporaryFile(key);
        Files.write(temporary, new byte[(int) bytes]);
        return cache.storeConverted(key, temporary, gain);
    }

    @Test
    void missThenHitReturnsTheStoredWavAndGain() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        CacheKey key = key("a");

        assertTrue(cache.lookup(key).isEmpty());
        Path wav = store(cache, key, 1000, 0.42);

        AudioCache.CachedAudio hit = cache.lookup(key).orElseThrow();
        assertEquals(Optional.of(wav), hit.wav());
        assertEquals(0.42, hit.gain());
    }

    @Test
    void gainOnlyEntriesHaveNoWav() {
        AudioCache cache = cache(100 * UNIT);

        cache.storeGain(key("mp3"), 0.7);

        AudioCache.CachedAudio hit = cache.lookup(key("mp3")).orElseThrow();
        assertTrue(hit.wav().isEmpty());
        assertEquals(0.7, hit.gain());
        assertEquals(0, cache.usedBytes());
    }

    @Test
    void entriesSurviveARestart() throws IOException {
        AudioCache first = cache(100 * UNIT);
        store(first, key("a"), 500, 0.3);
        first.storeGain(key("b"), 0.9);
        first.flush();

        AudioCache second = cache(100 * UNIT);

        assertEquals(0.3, second.lookup(key("a")).orElseThrow().gain());
        assertEquals(0.9, second.lookup(key("b")).orElseThrow().gain());
    }

    @Test
    void deletedWavWithSurvivingMetadataIsAMiss() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        Path wav = store(cache, key("a"), 500, 0.3);

        Files.delete(wav);

        assertTrue(cache.lookup(key("a")).isEmpty());
    }

    @Test
    void leastRecentlyUsedEntryIsEvictedFirst() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        store(cache, key("a"), (int) (40 * UNIT), 1);
        store(cache, key("b"), (int) (40 * UNIT), 1);
        store(cache, key("c"), (int) (15 * UNIT), 1);

        store(cache, key("d"), (int) (30 * UNIT), 1);

        assertTrue(cache.lookup(key("a")).isEmpty());
        assertTrue(cache.lookup(key("d")).isPresent());
        assertEquals(85 * UNIT, cache.usedBytes());
    }

    @Test
    void aHitMakesAnEntryNewerThanTheOthers() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        store(cache, key("a"), (int) (40 * UNIT), 1);
        store(cache, key("b"), (int) (40 * UNIT), 1);
        cache.lookup(key("a"));

        store(cache, key("c"), (int) (30 * UNIT), 1);

        assertTrue(cache.lookup(key("a")).isPresent());
        assertTrue(cache.lookup(key("b")).isEmpty());
    }

    @Test
    void loweringTheLimitEvictsImmediately() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        store(cache, key("a"), (int) (40 * UNIT), 1);
        store(cache, key("b"), (int) (40 * UNIT), 1);

        cache.setLimitBytes(50 * UNIT);

        assertEquals(40 * UNIT, cache.usedBytes());
        assertTrue(cache.lookup(key("a")).isEmpty());
    }

    @Test
    void undeletableEntriesAreSkippedAndTheNextOldestGoes() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        Path oldest = store(cache, key("a"), (int) (40 * UNIT), 1);
        store(cache, key("b"), (int) (40 * UNIT), 1);
        undeletable.add(oldest);

        store(cache, key("c"), (int) (40 * UNIT), 1);

        assertTrue(cache.lookup(key("a")).isPresent());
        assertTrue(cache.lookup(key("b")).isEmpty());
    }

    @Test
    void theEntryJustStoredIsKeptEvenWhenItAloneExceedsTheLimit() throws IOException {
        AudioCache cache = cache(50 * UNIT);

        store(cache, key("big"), (int) (80 * UNIT), 1);

        assertTrue(cache.lookup(key("big")).isPresent());
        store(cache, key("small"), (int) UNIT, 1);
        assertTrue(cache.lookup(key("big")).isEmpty());
    }

    @Test
    void startupRemovesTemporaryFilesAndOrphanWavs() throws IOException {
        Files.createDirectories(dir);
        Path temporary = Files.write(dir.resolve("x.wav.tmp"), new byte[10]);
        Path orphan = Files.write(dir.resolve(key("orphan").hex() + AudioCache.WAV_SUFFIX), new byte[10]);

        cache(100 * UNIT);

        assertFalse(Files.exists(temporary));
        assertFalse(Files.exists(orphan));
    }

    @Test
    void corruptIndexStartsEmptyAndRemovesOrphans() throws IOException {
        AudioCache first = cache(100 * UNIT);
        Path wav = store(first, key("a"), 500, 1);
        first.flush();
        Files.writeString(dir.resolve(AudioCache.INDEX_FILE), "total garbage");

        AudioCache second = cache(100 * UNIT);

        assertTrue(second.lookup(key("a")).isEmpty());
        assertFalse(Files.exists(wav));
    }

    @Test
    void clearRemovesWavsExceptTheOnesThatCannotBeDeleted() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        Path locked = store(cache, key("a"), 500, 1);
        store(cache, key("b"), 500, 1);
        undeletable.add(locked);

        cache.clear();

        assertTrue(cache.lookup(key("a")).isPresent());
        assertTrue(cache.lookup(key("b")).isEmpty());
    }

    @Test
    void writesScheduleOneDebouncedFlush() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        int before = scheduledFlushes;

        store(cache, key("a"), 10, 1);
        cache.storeGain(key("b"), 1);

        assertEquals(before + 1, scheduledFlushes);
    }

    @Test
    void aFailedMoveLeavesNoTemporaryFileBehind() throws IOException {
        AudioCache cache = cache(100 * UNIT);
        CacheKey key = key("a");
        Path missing = dir.resolve("never-created.tmp");

        try {
            cache.storeConverted(key, missing, 1);
        } catch (IOException expected) {
            assertTrue(cache.lookup(key).isEmpty());
        }
    }
}
