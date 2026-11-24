package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.NowPlayingSource;

enum ReopenTarget {
    PREVIEW,
    PLAYLIST,
    ON_DEMAND,
    NONE;

    static ReopenTarget of(NowPlayingSource source) {
        return switch (source) {
            case PREVIEW -> PREVIEW;
            case PLAYLIST -> PLAYLIST;
            case ON_DEMAND -> ON_DEMAND;
            case MOOD, NONE -> NONE;
        };
    }
}
