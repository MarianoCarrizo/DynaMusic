package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

public final class OnDemandPanelController {

    @FXML private VBox onDemandPanel;
    @FXML private Label lblOnDemandTrack;
    @FXML private ToggleButton btnOnDemandLoop;
    @FXML private Button btnOnDemandPlayPause;
    @FXML private SeekBar onDemandSeekBar;

    private PlayerHost host;

    void attach(PlayerHost host) {
        this.host = host;
        MusicService musicService = host.musicService();
        musicService.setOnDemandProgressListener((current, total) ->
            Platform.runLater(() -> {
                onDemandSeekBar.showPlayback(current, total);
                syncPlayPauseIcon();
            }));
        musicService.setOnDemandStateListener(() -> {
            if (!musicService.isOnDemandPlaying()) {
                onDemandSeekBar.rewindToStart();
            }
            syncPlayPauseIcon();
        });
        onDemandSeekBar.setOnSeekToFraction(musicService::seekOnDemand);
        show(false);
    }

    void showTrack(String label) {
        lblOnDemandTrack.setText(label);
        onDemandSeekBar.showNoTrack();
    }

    void show(boolean show) {
        onDemandPanel.setVisible(show);
        onDemandPanel.setManaged(show);
    }

    void showPlaying(boolean playing) {
        btnOnDemandPlayPause.setText(playing ? "⏸" : "▶");
    }

    void syncPlayPauseIcon() {
        showPlaying(host.musicService().isOnDemandPlaying());
    }

    @FXML
    void togglePlayPause() {
        MusicService musicService = host.musicService();
        if (musicService.isOnDemandPlaying()) musicService.onDemandPause();
        else musicService.onDemandPlay();
        syncPlayPauseIcon();
    }

    @FXML
    private void onOnDemandStop() {
        MusicService musicService = host.musicService();
        musicService.onDemandPause();
        musicService.seekOnDemand(0);
        onDemandSeekBar.rewindToStart();
        syncPlayPauseIcon();
    }

    @FXML
    private void onOnDemandClose() {
        host.closeOnDemand();
    }

    @FXML
    private void onToggleOnDemandLoop() {
        host.musicService().setOnDemandLoop(btnOnDemandLoop.isSelected());
    }
}
