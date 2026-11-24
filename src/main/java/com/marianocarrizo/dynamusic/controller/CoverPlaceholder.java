package com.marianocarrizo.dynamusic.controller;

final class CoverPlaceholder {

    private static final String UNKNOWN = "Unknown album";

    private CoverPlaceholder() {
    }

    static String caption(boolean resolved, boolean hasArt, String albumTitle) {
        if (!resolved || hasArt) return "";
        return albumTitle == null || albumTitle.isBlank() ? UNKNOWN : albumTitle;
    }
}
