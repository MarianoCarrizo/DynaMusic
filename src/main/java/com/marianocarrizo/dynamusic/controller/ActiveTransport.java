package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;

import java.util.function.BooleanSupplier;

final class ActiveTransport {

    private final MusicService musicService;
    private final Runnable startPlaylist;
    private final BooleanSupplier moodAllowed;

    ActiveTransport(MusicService musicService, Runnable startPlaylist, BooleanSupplier moodAllowed) {
        this.musicService = musicService;
        this.startPlaylist = startPlaylist;
        this.moodAllowed = moodAllowed;
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

    void seek(double fraction) {
        switch (musicService.activeSource()) {
            case PREVIEW -> musicService.seekPreview(fraction);
            case MOOD -> seekMood(fraction);
            case PLAYLIST -> musicService.seekPlaylist(fraction);
            case ON_DEMAND -> musicService.seekOnDemand(fraction);
            default -> { }
        }
    }

    private void togglePreview() {
        if (musicService.isPreviewPlaying()) musicService.previewPause();
        else musicService.previewPlay();
    }

    private void seekMood(double fraction) {
        if (moodAllowed.getAsBoolean()) musicService.seekCurrentTrack(fraction);
    }

    private void toggleMood() {
        if (!moodAllowed.getAsBoolean()) return;
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
}
