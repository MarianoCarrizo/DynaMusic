package com.marianocarrizo.dynamusic.controller;

enum RowPlayback {
    NONE(""),
    PLAYING("▶"),
    PAUSED("⏸"),
    LOADING("…");

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
        return of(currentRow, playing, false);
    }

    static RowPlayback of(boolean currentRow, boolean playing, boolean loading) {
        if (!currentRow) return NONE;
        if (loading) return LOADING;
        return playing ? PLAYING : PAUSED;
    }
}
