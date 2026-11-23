package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.PlayPauseButton;
import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.TrackNames;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

import java.io.File;

public final class PreviewPanelController {

    private static final String NO_PREVIEW_TEXT = "No active preview";

    interface Host {
        MusicService musicService();

        NowPlayingModel nowPlaying();
    }

    @FXML private VBox previewPanel;
    @FXML private Label lblPreviewTrack;
    @FXML private ToggleButton btnPreviewLoop;
    @FXML private PlayPauseButton btnPreviewPlayPause;
    @FXML private SeekBar previewSeekBar;

    private Host host;

    void attach(Host host) {
        this.host = host;
        MusicService musicService = host.musicService();
        musicService.setPreviewProgressListener(previewSeekBar::showPlayback);
        previewSeekBar.setOnSeekToFraction(musicService::seekPreview);
        host.nowPlaying().snapshotProperty().addListener((obs, oldValue, newValue) -> syncPlayPauseIcon());
        show(false);
    }

    void start(File file) {
        lblPreviewTrack.setText(TrackNames.title(file.getName()));
        host.musicService().previewTrack(file);
        show(true);
        syncPlayPauseIcon();
    }

    void hide() {
        show(false);
        previewSeekBar.showNoTrack();
        syncPlayPauseIcon();
    }

    @FXML
    private void onTogglePlayPause() {
        MusicService musicService = host.musicService();
        if (musicService.isPreviewPlaying()) musicService.previewPause();
        else musicService.previewPlay();
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

    private void syncPlayPauseIcon() {
        btnPreviewPlayPause.showPlaying(host.musicService().isPreviewPlaying());
    }
}
