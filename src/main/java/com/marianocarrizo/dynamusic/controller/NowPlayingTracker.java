package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.service.PlaybackState;
import com.marianocarrizo.dynamusic.service.SoundingTrack;

import java.util.function.Supplier;

final class NowPlayingTracker {

    private final Supplier<PlaybackState> playbackState;
    private final Supplier<String> activePlaylistName;
    private final NowPlayingModel model;

    NowPlayingTracker(Supplier<PlaybackState> playbackState, Supplier<String> activePlaylistName,
                      NowPlayingModel model) {
        this.playbackState = playbackState;
        this.activePlaylistName = activePlaylistName;
        this.model = model;
    }

    void sync() {
        model.show(decide(playbackState.get(), activePlaylistName.get()));
    }

    static NowPlaying decide(PlaybackState state, String playlistName) {
        if (state.previewSoundedLast()) {
            return of(NowPlayingSource.PREVIEW, "", state.preview());
        }
        return switch (state.mode()) {
            case MOOD -> of(NowPlayingSource.MOOD, state.moodType() == null ? "" : state.moodType().name(),
                    state.mood());
            case PLAYLIST -> of(NowPlayingSource.PLAYLIST, playlistName, state.playlist());
            case ON_DEMAND -> of(NowPlayingSource.ON_DEMAND, "", state.onDemand());
        };
    }

    private static NowPlaying of(NowPlayingSource source, String sourceName, SoundingTrack track) {
        if (!track.isPresent()) return NowPlaying.NONE;
        return new NowPlaying(source, sourceName, track.title(), track.detail(), track.file(), track.playing());
    }
}
