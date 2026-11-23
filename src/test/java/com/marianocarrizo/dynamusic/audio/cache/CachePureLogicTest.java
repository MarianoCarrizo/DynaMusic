package com.marianocarrizo.dynamusic.audio.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachePureLogicTest {

    private static final String KEY_A = "a".repeat(64);
    private static final String KEY_B = "b".repeat(64);
    private static final String KEY_C = "c".repeat(64);
    private static final String KEY_D = "d".repeat(64);

    @TempDir Path dir;

    @Test
    void keyIsStableForTheSameTripleAndChangesWithAnyComponent() {
        File file = new File("music", "Song.flac");

        CacheKey base = CacheKey.of(file, 100, 5000);

        assertEquals(base, CacheKey.of(file, 100, 5000));
        assertNotEquals(base, CacheKey.of(file, 101, 5000));
        assertNotEquals(base, CacheKey.of(file, 100, 5001));
        assertNotEquals(base, CacheKey.of(new File("music", "Other.flac"), 100, 5000));
    }

    @Test
    void keyIsAFilesystemSafeSha256Hex() {
        String hex = CacheKey.of(new File("a b é [x].flac"), 1, 1).hex();

        assertTrue(hex.matches("[0-9a-f]{64}"));
    }

    @Test
    void keyIgnoresPathCase() {
        assertEquals(CacheKey.of(new File("Music/SONG.flac"), 1, 1), CacheKey.of(new File("music/song.flac"), 1, 1));
    }

    @Test
    void keyFollowsTheFileOnDisk() throws IOException {
        Path file = Files.writeString(dir.resolve("a.flac"), "one");
        CacheKey before = CacheKey.of(file.toFile());

        Files.writeString(file, "longer content");

        assertNotEquals(before, CacheKey.of(file.toFile()));
    }

    @Test
    void limitsAreAClosedSetWithTwoGigabytesAsTheDefault() {
        assertEquals(CacheLimit.GB_2, CacheLimit.DEFAULT);
        assertEquals(2L * 1024 * 1024 * 1024, CacheLimit.DEFAULT.bytes());
        assertEquals(CacheLimit.GB_5, CacheLimit.fromName("GB_5"));
        assertEquals(CacheLimit.DEFAULT, CacheLimit.fromName(null));
        assertEquals(CacheLimit.DEFAULT, CacheLimit.fromName("bogus"));
        assertEquals("512 MB", CacheLimit.MB_512.toString());
    }

    @Test
    void codecRoundTripsEntries() {
        List<CacheEntry> entries = List.of(new CacheEntry(KEY_A, 0.5, 1000, 7), new CacheEntry(KEY_B, 1.0, 0, 9));

        assertEquals(entries, CacheIndexCodec.decode(CacheIndexCodec.encode(entries)));
    }

    @Test
    void codecDropsCorruptContentInsteadOfFailing() {
        assertTrue(CacheIndexCodec.decode("garbage").isEmpty());
        assertTrue(CacheIndexCodec.decode("").isEmpty());
        assertTrue(CacheIndexCodec.decode(null).isEmpty());
        String text = CacheIndexCodec.HEADER + "\nnot-a-key\t1\t1\t1\n" + KEY_A + "\tNaN\t1\t1\n" + KEY_B
                + "\t0.5\t-4\t1\n" + KEY_C + "\t0.5\t10\t3\n" + KEY_D + "\t0.5\n";

        List<CacheEntry> decoded = CacheIndexCodec.decode(text);

        assertEquals(1, decoded.size());
        assertEquals(KEY_C, decoded.get(0).key());
    }

    private static List<CacheEntry> hundredMegabyteExample() {
        long mb = 1024 * 1024;
        return List.of(new CacheEntry(KEY_A, 1, 40 * mb, 1), new CacheEntry(KEY_B, 1, 40 * mb, 2),
                new CacheEntry(KEY_C, 1, 15 * mb, 3), new CacheEntry(KEY_D, 1, 30 * mb, 4));
    }

    @Test
    void oldestEntryIsEvictedFirst() {
        long limit = 100L * 1024 * 1024;

        assertEquals(List.of(KEY_A), LruPlan.wavVictims(hundredMegabyteExample(), limit, Set.of()));
    }

    @Test
    void severalEntriesAreEvictedWhenOneIsNotEnough() {
        long mb = 1024 * 1024;
        List<CacheEntry> entries = List.of(new CacheEntry(KEY_A, 1, 40 * mb, 1), new CacheEntry(KEY_B, 1, 40 * mb, 2),
                new CacheEntry(KEY_C, 1, 15 * mb, 3), new CacheEntry(KEY_D, 1, 70 * mb, 4));

        assertEquals(List.of(KEY_A, KEY_B), LruPlan.wavVictims(entries, 100 * mb, Set.of()));
    }

    @Test
    void excludedEntriesAreSkippedAndTheNextOldestGoes() {
        long limit = 100L * 1024 * 1024;

        assertEquals(List.of(KEY_B), LruPlan.wavVictims(hundredMegabyteExample(), limit, Set.of(KEY_A)));
    }

    @Test
    void nothingIsEvictedWithinTheLimit() {
        assertTrue(LruPlan.wavVictims(hundredMegabyteExample(), Long.MAX_VALUE, Set.of()).isEmpty());
    }

    @Test
    void gainOnlyEntriesAreCappedByAge() {
        List<CacheEntry> entries = new ArrayList<>();
        for (int i = 0; i < LruPlan.MAX_GAIN_ONLY_ENTRIES + 2; i++) {
            entries.add(new CacheEntry(String.format("%064x", i), 1, 0, i));
        }

        List<String> victims = LruPlan.gainOnlyVictims(entries);

        assertEquals(List.of(String.format("%064x", 0), String.format("%064x", 1)), victims);
    }
}
