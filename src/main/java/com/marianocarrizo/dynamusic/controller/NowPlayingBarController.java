package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.PlayPauseButton;
import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public final class NowPlayingBarController {

    interface Host {
        MusicService musicService();

        NowPlayingModel nowPlaying();

        void togglePlayback();

        void stopPlayback();

        void playPrevious();

        void playNext();

        void seekActive(double fraction);
    }

    @FXML private Label lblSourceBadge;
    @FXML private Label lblTitle;
    @FXML private Label lblDetail;
    @FXML private Button btnPrevious;
    @FXML private PlayPauseButton btnPlayPause;
    @FXML private Button btnNext;
    @FXML private Button btnStop;
    @FXML private SeekBar seekBar;

    private Host host;
    private NowPlaying shown = NowPlaying.NONE;

    void attach(Host host) {
        this.host = host;
        seekBar.setOnSeekToFraction(host::seekActive);
        host.musicService().setActiveProgressListener(seekBar::showPlayback);
        host.nowPlaying().snapshotProperty().addListener((obs, oldValue, newValue) -> render(newValue));
        render(host.nowPlaying().get());
    }

    @FXML
    private void onTogglePlayPause() {
        host.togglePlayback();
    }

    @FXML
    private void onStop() {
        host.stopPlayback();
    }

    @FXML
    private void onPrevious() {
        host.playPrevious();
    }

    @FXML
    private void onNext() {
        host.playNext();
    }

    private void render(NowPlaying current) {
        lblSourceBadge.setText(current.badge());
        lblTitle.setText(current.title());
        showDetail(current.detail());
        btnPlayPause.showPlaying(current.playing());
        boolean playlist = current.source() == NowPlayingSource.PLAYLIST;
        btnPrevious.setDisable(!playlist);
        btnNext.setDisable(!playlist);
        boolean idle = current.source() == NowPlayingSource.NONE;
        btnStop.setDisable(idle);
        seekBar.setSeekEnabled(!idle);
        showProgress(current, idle);
        shown = current;
    }

    private void showDetail(String detail) {
        lblDetail.setText(detail);
        lblDetail.setVisible(!detail.isEmpty());
        lblDetail.setManaged(!detail.isEmpty());
    }

    private void showProgress(NowPlaying current, boolean idle) {
        boolean sameTrack = current.source() == shown.source() && current.title().equals(shown.title());
        if (idle || !sameTrack) seekBar.showNoTrack();
        if (!idle) host.musicService().reportActiveProgress();
    }
}
