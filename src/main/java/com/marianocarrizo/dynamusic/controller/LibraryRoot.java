package com.marianocarrizo.dynamusic.controller;

import java.io.File;
import java.nio.file.Path;

final class LibraryRoot {

    private LibraryRoot() {
    }

    static File rootFor(File currentRoot, File file) {
        if (currentRoot != null && contains(currentRoot, file)) return currentRoot;
        return file.getAbsoluteFile().getParentFile();
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
