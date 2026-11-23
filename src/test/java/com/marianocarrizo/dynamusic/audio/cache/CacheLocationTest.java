package com.marianocarrizo.dynamusic.audio.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheLocationTest {

    @TempDir Path dir;

    @Test
    void anOldRoamingCacheIsDeletedOnce() throws IOException {
        Path legacy = dir.resolve("roaming").resolve("cache").resolve("audio");
        Files.createDirectories(legacy.resolve("nested"));
        Files.write(legacy.resolve("a.wav"), new byte[100]);
        Files.write(legacy.resolve("nested").resolve("b.tmp"), new byte[10]);
        Path current = dir.resolve("local").resolve("cache").resolve("audio");

        LegacyCacheCleanup.deleteOnce(legacy, current);

        assertFalse(Files.exists(legacy));
        assertFalse(Files.exists(legacy.getParent()));
        assertTrue(Files.exists(dir.resolve("roaming")));
    }

    @Test
    void theCacheFolderIsKeptWhenOtherDataLivesNextToIt() throws IOException {
        Path legacy = dir.resolve("roaming").resolve("cache").resolve("audio");
        Files.createDirectories(legacy);
        Files.createDirectories(dir.resolve("roaming").resolve("cache").resolve("other"));

        LegacyCacheCleanup.deleteOnce(legacy, dir.resolve("local"));

        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(dir.resolve("roaming").resolve("cache").resolve("other")));
    }

    @Test
    void nothingHappensWhenThereIsNoOldCacheOrItIsTheCurrentOne() throws IOException {
        Path same = dir.resolve("same");
        Files.createDirectories(same);
        Files.write(same.resolve("a.wav"), new byte[10]);

        LegacyCacheCleanup.deleteOnce(same, same);
        LegacyCacheCleanup.deleteOnce(dir.resolve("missing"), same);

        assertTrue(Files.exists(same.resolve("a.wav")));
    }
}
