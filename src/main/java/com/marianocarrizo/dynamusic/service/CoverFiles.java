package com.marianocarrizo.dynamusic.service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CoverFiles {

    private static final List<String> NAMES = List.of("cover", "folder", "front");
    private static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "png");

    private CoverFiles() {
    }

    public static Optional<File> find(File folder) {
        File[] entries = folder == null ? null : folder.listFiles(File::isFile);
        if (entries == null) return Optional.empty();
        Map<String, File> byLowerName = new HashMap<>();
        for (File entry : entries) byLowerName.putIfAbsent(entry.getName().toLowerCase(Locale.ROOT), entry);
        for (String name : NAMES) {
            for (String extension : EXTENSIONS) {
                File match = byLowerName.get(name + "." + extension);
                if (match != null) return Optional.of(match);
            }
        }
        return Optional.empty();
    }
}
