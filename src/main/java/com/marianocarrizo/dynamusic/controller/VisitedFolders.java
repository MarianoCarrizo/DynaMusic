package com.marianocarrizo.dynamusic.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

final class VisitedFolders {

    private final Set<Path> seen = new HashSet<>();

    boolean firstVisit(File folder) {
        try {
            return seen.add(folder.toPath().toRealPath());
        } catch (IOException | InvalidPathException e) {
            return false;
        }
    }
}
