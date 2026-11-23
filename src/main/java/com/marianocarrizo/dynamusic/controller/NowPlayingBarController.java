package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.CoverView;
import com.marianocarrizo.dynamusic.component.IconButton;
import com.marianocarrizo.dynamusic.component.IconToggleButton;
import com.marianocarrizo.dynamusic.component.Icons;
import com.marianocarrizo.dynamusic.component.PlayPauseButton;
import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.model.LoadState;
import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.RepeatMode;
import com.marianocarrizo.dynamusic.service.CoverArtLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;

import java.io.File;
import java.util.Objects;

public final class NowPlayingBarController {

    private static final String PLACEHOLDER_RESOURCE = "/images/cover-placeholder.png";
    private static final int PLACEHOLDER_PIXELS = 112;
    private static final String FAILED_DETAIL_STYLE = "now-playing-failed";

    interface Host extends VolumeController.Host, TrackActionsMenu.Host {
        NowPlayingModel nowPlaying();

        void togglePlayback();

        void playPrevious();

        void playNext();

        void seekActive(double fraction);

        boolean isShuffleOn();

        void setShuffle(boolean on);

        RepeatMode repeatMode(NowPlayingSource source);

        RepeatMode cycleRepeat(NowPlayingSource source);
    }

    @FXML private HBox trackInfo;
    @FXML private CoverView cover;
    @FXML private Label lblTitle;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label lblDetail;
    @FXML private Label lblSource;
    @FXML private IconToggleButton btnShuffle;
    @FXML private IconButton btnPrevious;
    @FXML private PlayPauseButton btnPlayPause;
    @FXML private IconButton btnNext;
    @FXML private IconToggleButton btnRepeat;
    @FXML private SeekBar seekBar;
    @FXML private VolumeController volumeController;

    private final CoverArtLoader coverArt = new CoverArtLoader();
    private Image placeholder;
    private Host host;
    private TrackActionsMenu trackMenu;
    private NowPlaying shown = NowPlaying.NONE;
    private File shownCoverFile;

    @FXML
    public void initialize() {
        placeholder = new Image(Objects.requireNonNull(getClass().getResource(PLACEHOLDER_RESOURCE)).toExternalForm(),
                PLACEHOLDER_PIXELS, PLACEHOLDER_PIXELS, true, true);
        cover.setImage(placeholder);
        btnShuffle.setIcon(Icons.SHUFFLE);
        btnShuffle.setHint("Shuffle");
        btnPrevious.setIcon(Icons.PREVIOUS);
        btnPrevious.setHint("Previous track");
        btnNext.setIcon(Icons.NEXT);
        btnNext.setHint("Next track");
        showRepeat(RepeatMode.OFF);
    }

    void attach(Host host) {
        this.host = host;
        trackMenu = new TrackActionsMenu(host);
        trackInfo.setOnContextMenuRequested(this::showTrackMenu);
        seekBar.setOnSeekToFraction(host::seekActive);
        host.musicService().setActiveProgressListener(seekBar::showPlayback);
        volumeController.attach(host);
        host.nowPlaying().snapshotProperty().addListener((obs, oldValue, newValue) -> render(newValue));
        render(host.nowPlaying().get());
    }

    private void showTrackMenu(ContextMenuEvent event) {
        File playing = host.nowPlaying().get().file();
        if (playing == null) return;
        trackMenu.show(trackInfo, playing, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    void restoreVolume(double volume) {
        volumeController.restore(volume);
    }

    @FXML
    private void onTogglePlayPause() {
        host.togglePlayback();
    }

    @FXML
    private void onPrevious() {
        host.playPrevious();
    }

    @FXML
    private void onNext() {
        host.playNext();
    }

    @FXML
    private void onShuffle() {
        host.setShuffle(btnShuffle.isSelected());
    }

    @FXML
    private void onRepeat() {
        showRepeat(host.cycleRepeat(host.nowPlaying().get().source()));
    }

    private void showRepeat(RepeatMode mode) {
        btnRepeat.setIcon(mode == RepeatMode.ONE ? Icons.REPEAT_ONE : Icons.REPEAT);
        btnRepeat.setHint(mode.hint());
        btnRepeat.setSelected(mode.isActive());
    }

    private void render(NowPlaying current) {
        lblTitle.setText(current.title());
        showText(lblDetail, current.detail());
        showText(lblSource, current.badge());
        btnPlayPause.showPlaying(current.playing());
        btnPlayPause.setDisable(current.isBusy());
        loadingIndicator.setVisible(current.isLoading());
        loadingIndicator.setManaged(current.isLoading());
        lblDetail.getStyleClass().remove(FAILED_DETAIL_STYLE);
        if (current.state() == LoadState.FAILED) lblDetail.getStyleClass().add(FAILED_DETAIL_STYLE);
        TransportAvailability available = TransportAvailability.of(current);
        TransportAvailability supported = TransportAvailability.of(current.source());
        btnShuffle.setDisable(!available.shuffle());
        btnPrevious.setDisable(!available.previous());
        btnNext.setDisable(!available.next());
        btnRepeat.setDisable(!available.repeat());
        btnShuffle.setSelected(supported.shuffle() && host.isShuffleOn());
        showRepeat(supported.repeat() ? host.repeatMode(current.source()) : RepeatMode.OFF);
        boolean idle = current.source() == NowPlayingSource.NONE;
        seekBar.setSeekEnabled(!idle && !current.isBusy());
        showProgress(current, idle);
        showCover(current.file());
        shown = current;
    }

    private void showText(Label label, String text) {
        label.setText(text);
        label.setVisible(!text.isEmpty());
        label.setManaged(!text.isEmpty());
    }

    private void showProgress(NowPlaying current, boolean idle) {
        boolean sameTrack = current.source() == shown.source() && current.title().equals(shown.title());
        if (idle || current.isBusy() || !sameTrack) seekBar.showNoTrack();
        if (!idle && !current.isBusy()) host.musicService().reportActiveProgress();
    }

    private void showCover(File file) {
        if (Objects.equals(file, shownCoverFile)) return;
        shownCoverFile = file;
        cover.setImage(placeholder);
        coverArt.load(file, image -> cover.setImage(image.orElse(placeholder)));
    }
}
