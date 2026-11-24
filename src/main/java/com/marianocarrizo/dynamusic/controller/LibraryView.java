package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;

enum LibraryView {
    FOLDERS,
    ALBUMS,
    ARTISTS;

    static LibraryView effective(AppMode mode, LibraryView selected) {
        return mode == AppMode.LISTEN ? selected : FOLDERS;
    }

    boolean browses() {
        return this != FOLDERS;
    }

    String status(String folderPath, String browseStatus) {
        return browses() ? browseStatus : folderPath;
    }
}
