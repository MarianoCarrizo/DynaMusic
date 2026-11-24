package com.marianocarrizo.dynamusic.service.playback;

import javafx.scene.media.MediaPlayer;

public enum PlaybackStatus {
    UNKNOWN,
    READY,
    PLAYING,
    PAUSED,
    STOPPED,
    STALLED,
    HALTED,
    DISPOSED;

    public static PlaybackStatus from(MediaPlayer.Status status) {
        if (status == null) return UNKNOWN;
        return switch (status) {
            case UNKNOWN -> UNKNOWN;
            case READY -> READY;
            case PLAYING -> PLAYING;
            case PAUSED -> PAUSED;
            case STOPPED -> STOPPED;
            case STALLED -> STALLED;
            case HALTED -> HALTED;
            case DISPOSED -> DISPOSED;
        };
    }
}
