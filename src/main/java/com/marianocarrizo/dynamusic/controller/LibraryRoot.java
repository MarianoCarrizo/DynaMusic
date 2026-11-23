package com.marianocarrizo.dynamusic.controller;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

final class LibraryRoot {

    private LibraryRoot() {
    }

    static Optional<File> rootFor(File currentRoot, File file) {
        if (currentRoot != null && contains(currentRoot, file)) return Optional.of(currentRoot);
        File folder = file.getAbsoluteFile().getParentFile();
        return folder == null || isFilesystemRoot(folder) ? Optional.empty() : Optional.of(folder);
    }

    static boolean isFilesystemRoot(File folder) {
        return normalized(folder).getParent() == null;
    }

    static boolean contains(File root, File file) {
        return normalized(file).startsWith(normalized(root));
    }

    static boolean isSameFile(File first, File second) {
        return normalized(first).equals(normalized(second));
    }

    private static Path normalized(File file) {
        return file.toPath().toAbsolutePath().normalize();
    }
}
