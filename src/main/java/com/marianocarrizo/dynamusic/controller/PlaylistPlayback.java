package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import javafx.application.Platform;

import java.io.File;
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
    }

    void play(File file) {
        enterMode.accept(MusicService.PlaybackMode.PLAYLIST);
        musicService.playPlaylistTrack(file);
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

    private void advanceAfterEnd() {
        playlistManager.next().ifPresentOrElse(musicService::playPlaylistTrack, musicService::stopPlaylist);
        refreshView.run();
    }
}
