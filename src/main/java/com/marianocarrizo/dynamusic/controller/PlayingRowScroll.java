package com.marianocarrizo.dynamusic.controller;

import java.util.Objects;

final class PlayingRowScroll {

    private static final int DEFAULT_ROWS_ABOVE = 3;

    private PlayingRowScroll() {
    }

    static boolean shouldScroll(Object previousTrack, Object playingTrack, int playingIndex, RowRange visible) {
        if (playingTrack == null || playingIndex < 0) return false;
        if (Objects.equals(previousTrack, playingTrack)) return false;
        return !visible.contains(playingIndex);
    }

    static int topIndexFor(int playingIndex, RowRange visible) {
        int rowsAbove = visible == RowRange.EMPTY ? DEFAULT_ROWS_ABOVE : visible.size() / 2;
        return Math.max(0, playingIndex - rowsAbove);
    }
}
