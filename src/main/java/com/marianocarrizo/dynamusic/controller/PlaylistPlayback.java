package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import javafx.application.Platform;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class PlaylistPlayback {

    private final MusicService musicService;
    private final PlaylistManager playlistManager;
    private final Consumer<MusicService.PlaybackMode> enterMode;
    private final Runnable refreshView;

    PlaylistPlayback(MusicService musicService, PlaylistManager playlistManager,
                     Consumer<MusicService.PlaybackMode> enterMode, Runnable refreshView) {
        this.musicService = musicService;
        this.playlistManager = playlistManager;
        this.enterMode = enterMode;
        this.refreshView = refreshView;
        musicService.setOnPlaylistEnd(() -> Platform.runLater(this::advanceAfterEnd));
        musicService.setOnPlaylistTrackFailed(() -> Platform.runLater(this::skipFailedTrack));
    }

    void playContext(String label, List<File> tracks, int start) {
        playlistManager.playTransient(label, tracks, start);
        startCurrent();
        refreshView.run();
    }

    void play(File file) {
        enterMode.accept(MusicService.PlaybackMode.PLAYLIST);
        playAndPrefetch(file);
    }

    private void skipAndPrefetch(File file) {
        musicService.skipToPlaylistTrack(file);
        playlistManager.peekNext().ifPresent(musicService::prefetch);
    }

    private void playAndPrefetch(File file) {
        musicService.playPlaylistTrack(file);
        playlistManager.peekNext().ifPresent(musicService::prefetch);
    }

    boolean startCurrent() {
        Optional<File> current = playlistManager.startCurrent();
        current.ifPresent(this::play);
        return current.isPresent();
    }

    void previous() {
        playlistManager.prev().ifPresent(this::play);
        refreshView.run();
    }

    void next() {
        playlistManager.next().ifPresentOrElse(this::play, musicService::stopPlaylist);
        refreshView.run();
    }

    private void skipFailedTrack() {
        playlistManager.next().ifPresentOrElse(this::skipAndPrefetch, musicService::stopPlaylist);
        refreshView.run();
    }

    private void advanceAfterEnd() {
        playlistManager.nextAfterEnd().ifPresentOrElse(this::playAndPrefetch, musicService::stopPlaylist);
        refreshView.run();
    }
}
