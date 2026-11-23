package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;

final class ActiveTransport {

    private final MusicService musicService;
    private final Runnable startPlaylist;

    ActiveTransport(MusicService musicService, Runnable startPlaylist) {
        this.musicService = musicService;
        this.startPlaylist = startPlaylist;
    }

    void togglePlayPause() {
        switch (musicService.activeSource()) {
            case PREVIEW -> togglePreview();
            case MOOD -> toggleMood();
            case PLAYLIST -> togglePlaylist();
            case ON_DEMAND -> toggleOnDemand();
            default -> { }
        }
    }

    void stop() {
        switch (musicService.activeSource()) {
            case PREVIEW -> musicService.stopPreview();
            case MOOD -> musicService.stop();
            case PLAYLIST -> musicService.stopPlaylist();
            case ON_DEMAND -> rewindOnDemand();
            default -> { }
        }
    }

    void seek(double fraction) {
        switch (musicService.activeSource()) {
            case PREVIEW -> musicService.seekPreview(fraction);
            case MOOD -> musicService.seekCurrentTrack(fraction);
            case PLAYLIST -> musicService.seekPlaylist(fraction);
            case ON_DEMAND -> musicService.seekOnDemand(fraction);
            default -> { }
        }
    }

    private void togglePreview() {
        if (musicService.isPreviewPlaying()) musicService.previewPause();
        else musicService.previewPlay();
    }

    private void toggleMood() {
        if (musicService.isPlaying()) musicService.fadeOutAndPause(null);
        else musicService.fadeInAndPlay();
    }

    private void togglePlaylist() {
        if (musicService.isPlaylistPlaying()) musicService.playlistPause();
        else if (musicService.hasPlaylistTrack()) musicService.playlistPlay();
        else startPlaylist.run();
    }

    private void toggleOnDemand() {
        if (musicService.isOnDemandPlaying()) musicService.onDemandPause();
        else musicService.onDemandPlay();
    }

    private void rewindOnDemand() {
        musicService.onDemandPause();
        musicService.seekOnDemand(0);
    }
}
