package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;

import java.io.File;
import java.util.Optional;

public final class PlaylistTransportController {

    interface Host {
        MusicService musicService();

        PlaylistManager playlistManager();

        void enterMode(MusicService.PlaybackMode mode);

        void refreshPlaylistView();
    }

    @FXML private ToggleButton btnShuffle;
    @FXML private ToggleButton btnPlaylistLoop;

    private Host host;

    void attach(Host host) {
        this.host = host;
        MusicService musicService = host.musicService();
        musicService.setOnPlaylistEnd(() -> Platform.runLater(() -> {
            host.playlistManager().next().ifPresentOrElse(musicService::playPlaylistTrack, musicService::stopPlaylist);
            host.refreshPlaylistView();
        }));
    }

    void play(File file) {
        host.enterMode(MusicService.PlaybackMode.PLAYLIST);
        host.musicService().playPlaylistTrack(file);
    }

    boolean startCurrent() {
        Optional<File> current = host.playlistManager().startCurrent();
        current.ifPresent(this::play);
        return current.isPresent();
    }

    void previous() {
        host.playlistManager().prev().ifPresent(this::play);
        host.refreshPlaylistView();
    }

    void next() {
        host.playlistManager().next().ifPresentOrElse(this::play, host.musicService()::stopPlaylist);
        host.refreshPlaylistView();
    }

    @FXML
    private void onToggleShuffle() {
        host.playlistManager().setShuffle(btnShuffle.isSelected());
        host.refreshPlaylistView();
    }

    @FXML
    private void onTogglePlaylistLoop() {
        host.playlistManager().setLoop(btnPlaylistLoop.isSelected());
    }
}
