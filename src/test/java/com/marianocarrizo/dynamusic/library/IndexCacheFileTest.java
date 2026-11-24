package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexCacheFileTest {

    @TempDir
    Path folder;

    private static TrackEntry entry(String path) {
        return new TrackEntry(path, 5, 6, new TrackTags("T", "A", "B", 1, "", false));
    }

    @Test
    void missingFileLoadsAsEmpty() {
        assertTrue(new IndexCacheFile(folder.resolve("none.tsv")).load().isEmpty());
    }

    @Test
    void savedEntriesLoadBackKeyedByPath() {
        IndexCacheFile cache = new IndexCacheFile(folder.resolve("cache").resolve("index.tsv"));

        cache.save(List.of(entry("/m/a.flac"), entry("/m/b.flac")));
        Map<String, TrackEntry> loaded = cache.load();

        assertEquals(2, loaded.size());
        assertEquals(entry("/m/a.flac"), loaded.get("/m/a.flac"));
    }

    @Test
    void saveReplacesThePreviousContentAndLeavesNoTemporaryFile() throws IOException {
        Path file = folder.resolve("index.tsv");
        IndexCacheFile cache = new IndexCacheFile(file);

        cache.save(List.of(entry("/m/a.flac")));
        cache.save(List.of(entry("/m/b.flac")));

        assertEquals(List.of("/m/b.flac"), List.copyOf(cache.load().keySet()));
        try (var children = Files.list(folder)) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void corruptFileLoadsAsEmptyWithoutThrowing() throws IOException {
        Path file = folder.resolve("index.tsv");
        Files.write(file, new byte[] {0, 1, 2, (byte) 0xff, (byte) 0xfe, 10, 13});

        assertTrue(new IndexCacheFile(file).load().isEmpty());
    }

    @Test
    void fileWithAnotherVersionLoadsAsEmpty() throws IOException {
        Path file = folder.resolve("index.tsv");
        Files.writeString(file, "dynamusic-library-index 99\n/m/a.flac\t1\t1\t1\t0\t\tt\ta\tb\n", StandardCharsets.UTF_8);

        assertTrue(new IndexCacheFile(file).load().isEmpty());
    }

    @Test
    void unwritableLocationDoesNotThrow() throws IOException {
        Path blocker = folder.resolve("blocker");
        Files.writeString(blocker, "file");
        IndexCacheFile cache = new IndexCacheFile(blocker.resolve("index.tsv"));

        cache.save(List.of(entry("/m/a.flac")));

        assertFalse(Files.exists(blocker.resolve("index.tsv")));
    }
}
