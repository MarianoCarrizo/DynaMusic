package com.marianocarrizo.dynamusic.controller;

enum RowPlayback {
    NONE(""),
    PLAYING("▶"),
    PAUSED("⏸");

    private final String marker;

    RowPlayback(String marker) {
        this.marker = marker;
    }

    String marker() {
        return marker;
    }

    boolean isCurrent() {
        return this != NONE;
    }

    static RowPlayback of(boolean currentRow, boolean playing) {
        if (!currentRow) return NONE;
        return playing ? PLAYING : PAUSED;
    }
}
