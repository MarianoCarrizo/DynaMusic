package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.service.MusicService.PlaybackMode;

final class WindowAccent {

    private WindowAccent() {
    }

    static String rootId(PlaybackMode mode, TrackType mood) {
        if (mode != PlaybackMode.MOOD || mood == null) return null;
        return switch (mood) {
            case PAUSED -> "window-paused";
            case RAGED -> "window-raged";
            case NORMAL -> "window-normal";
            case ADVANTAGE -> "window-advantage";
        };
    }
}
