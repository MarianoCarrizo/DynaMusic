package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

import java.io.File;

public final class PreviewPanelController {

    private static final String NO_PREVIEW_TEXT = "No active preview";

    @FXML private VBox previewPanel;
    @FXML private Label lblPreviewTrack;
    @FXML private ToggleButton btnPreviewLoop;
    @FXML private Button btnPreviewPlayPause;
    @FXML private SeekBar previewSeekBar;

    private PlayerHost host;

    void attach(PlayerHost host) {
        this.host = host;
        MusicService musicService = host.musicService();
        musicService.setPreviewProgressListener((current, total) ->
            Platform.runLater(() -> {
                previewSeekBar.showPlayback(current, total);
                syncPlayPauseIcon();
            }));
        previewSeekBar.setOnSeekToFraction(musicService::seekPreview);
        musicService.setOnPreviewPausedByMode(() -> showPlaying(false));
        show(false);
    }

    void start(File file) {
        MusicService musicService = host.musicService();
        if (musicService.isPlaying()) musicService.fadeOutAndPause(null);
        if (musicService.isPlaylistPlaying()) musicService.playlistPause();
        lblPreviewTrack.setText(file.getName());
        musicService.previewTrack(file);
        show(true);
        syncPlayPauseIcon();
    }

    void hide() {
        show(false);
        previewSeekBar.showNoTrack();
        syncPlayPauseIcon();
    }

    @FXML
    void togglePlayPause() {
        MusicService musicService = host.musicService();
        if (musicService.isPreviewPlaying()) {
            musicService.previewPause();
            showPlaying(false);
        } else {
            musicService.previewPlay();
            showPlaying(true);
        }
    }

    @FXML
    private void onPreviewStop() {
        host.musicService().stopPreview();
        hide();
    }

    @FXML
    private void onTogglePreviewLoop() {
        host.musicService().setPreviewLoop(btnPreviewLoop.isSelected());
    }

    private void show(boolean active) {
        previewPanel.setVisible(active);
        previewPanel.setManaged(active);
        btnPreviewPlayPause.setDisable(!active);
        previewSeekBar.setSeekEnabled(active);
        if (!active) lblPreviewTrack.setText(NO_PREVIEW_TEXT);
    }

    private void showPlaying(boolean playing) {
        btnPreviewPlayPause.setText(playing ? "⏸" : "▶");
    }

    private void syncPlayPauseIcon() {
        showPlaying(host.musicService().isPreviewPlaying());
    }
}
