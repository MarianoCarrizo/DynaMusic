package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;

import java.io.File;

public final class PlaylistTransportController {

    @FXML private ToggleButton btnShuffle;
    @FXML private Button btnPrev;
    @FXML private Button btnPlaylistPlayPause;
    @FXML private Button btnNext;
    @FXML private ToggleButton btnPlaylistLoop;
    @FXML private SeekBar playlistSeekBar;

    private PlayerHost host;

    void attach(PlayerHost host) {
        this.host = host;
        MusicService musicService = host.musicService();
        PlaylistManager playlistManager = host.playlistManager();
        playlistSeekBar.setOnSeekToFraction(musicService::seekPlaylist);
        musicService.setOnPlaylistEnd(() -> Platform.runLater(() -> {
            playlistManager.next().ifPresentOrElse(
                musicService::playPlaylistTrack,
                () -> { musicService.stopPlaylist(); host.refreshPlaylistView(); }
            );
            host.refreshPlaylistView();
        }));
        musicService.setPlaylistProgressListener((current, total) ->
            Platform.runLater(() -> playlistSeekBar.showPlayback(current, total)));
    }

    void showPlaying(boolean playing) {
        btnPlaylistPlayPause.setText(playing ? "⏸" : "▶");
    }

    void play(File file) {
        host.enterMode(MusicService.PlaybackMode.PLAYLIST);
        host.musicService().playPlaylistTrack(file);
        showPlaying(true);
    }

    @FXML
    void togglePlayPause() {
        MusicService musicService = host.musicService();
        if (musicService.isPlaylistPlaying()) {
            musicService.playlistPause();
            showPlaying(false);
        } else {
            host.playlistManager().startCurrent().ifPresent(this::play);
        }
    }

    @FXML
    private void onPrev() {
        host.playlistManager().prev().ifPresent(this::play);
        host.refreshPlaylistView();
    }

    @FXML
    private void onNext() {
        host.playlistManager().next().ifPresentOrElse(this::play, () -> {
            host.musicService().stopPlaylist();
            showPlaying(false);
        });
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
