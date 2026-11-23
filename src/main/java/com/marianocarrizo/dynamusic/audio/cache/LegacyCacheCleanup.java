package com.marianocarrizo.dynamusic.audio.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class LegacyCacheCleanup {

    private LegacyCacheCleanup() {}

    public static void deleteOnce(Path legacy, Path current) {
        if (legacy.equals(current) || !Files.isDirectory(legacy)) return;
        try (Stream<Path> tree = Files.walk(legacy)) {
            for (Path entry : tree.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
        } catch (IOException | RuntimeException partial) {
            System.err.println("[audio-cache] could not remove the old cache folder: " + partial);
            return;
        }
        removeIfEmpty(legacy.getParent());
    }

    private static void removeIfEmpty(Path folder) {
        if (folder == null) return;
        try {
            Files.deleteIfExists(folder);
        } catch (IOException notEmpty) {
            return;
        }
    }
}
