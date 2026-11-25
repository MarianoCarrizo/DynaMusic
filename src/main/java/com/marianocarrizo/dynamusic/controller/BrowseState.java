package com.marianocarrizo.dynamusic.controller;

enum BrowseState {
    NO_FOLDER("Choose your music folder to browse albums and artists."),
    INDEXING("Indexing your library..."),
    EMPTY("No audio files found in this folder."),
    READY("");

    private final String message;

    BrowseState(String message) {
        this.message = message;
    }

    String message() {
        return message;
    }

    boolean offersFolder() {
        return this == NO_FOLDER;
    }

    boolean showsPlaceholder() {
        return this != READY;
    }

    static BrowseState of(boolean hasFolder, boolean indexing, int trackCount) {
        if (!hasFolder) return NO_FOLDER;
        if (trackCount > 0) return READY;
        return indexing ? INDEXING : EMPTY;
    }
}
