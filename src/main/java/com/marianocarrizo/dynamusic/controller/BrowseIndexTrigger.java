package com.marianocarrizo.dynamusic.controller;

import java.io.File;
import java.util.Optional;

final class BrowseIndexTrigger {

    private File opened;

    Optional<File> due(LibraryView view, File libraryRoot) {
        if (!view.browses() || libraryRoot == null || libraryRoot.equals(opened)) return Optional.empty();
        opened = libraryRoot;
        return Optional.of(libraryRoot);
    }
}
