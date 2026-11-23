package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;

import java.util.function.Supplier;

final class NowPlayingTracker {

    private final MusicService musicService;
    private final PlaylistManager playlistManager;
    private final Supplier<TrackType> activeMood;
    private final NowPlayingModel model;

    NowPlayingTracker(MusicService musicService, PlaylistManager playlistManager,
                      Supplier<TrackType> activeMood, NowPlayingModel model) {
        this.musicService = musicService;
        this.playlistManager = playlistManager;
        this.activeMood = activeMood;
        this.model = model;
    }

    void sync() {
        if (musicService.previewSoundedLast()) {
            model.show(NowPlayingSource.PREVIEW, "", musicService.isPreviewPlaying());
            return;
        }
        switch (musicService.getPlaybackMode()) {
            case MOOD -> showMood();
            case PLAYLIST -> model.show(NowPlayingSource.PLAYLIST,
                    playlistManager.getActivePlaylistName(), musicService.isPlaylistPlaying());
            case ON_DEMAND -> model.show(NowPlayingSource.ON_DEMAND, "", musicService.isOnDemandPlaying());
        }
    }

    private void showMood() {
        TrackType mood = activeMood.get();
        NowPlayingSource source = musicService.isLoaded(mood) ? NowPlayingSource.MOOD : NowPlayingSource.NONE;
        model.show(source, mood.name(), musicService.isPlaying());
    }
}
