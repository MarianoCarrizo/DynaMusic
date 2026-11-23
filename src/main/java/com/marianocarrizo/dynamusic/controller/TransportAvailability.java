package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;

record TransportAvailability(boolean shuffle, boolean previous, boolean next, boolean repeat) {

    static TransportAvailability of(NowPlaying current) {
        if (current.isBusy()) return new TransportAvailability(false, false, false, false);
        return of(current.source());
    }

    static TransportAvailability of(NowPlayingSource source) {
        boolean playlist = source == NowPlayingSource.PLAYLIST;
        return new TransportAvailability(playlist, playlist, playlist, source != NowPlayingSource.NONE);
    }
}
