package com.marianocarrizo.dynamusic.component;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.DoubleConsumer;

public final class SeekBar extends HBox {

    private static final String ZERO_TIME = "0:00";
    private static final Duration SEEK_COOLDOWN = Duration.millis(150);

    @FXML private Label lblCurrentTime;
    @FXML private Slider slider;
    @FXML private Label lblTotalTime;

    private DoubleConsumer onSeekToFraction = fraction -> { };
    private final PauseTransition seekCooldown = new PauseTransition(SEEK_COOLDOWN);
    private double pendingSeek = Double.NaN;
    private boolean showingPlayback = false;

    public SeekBar() {
        FXMLLoader loader = new FXMLLoader(SeekBar.class.getResource("/views/component/seek_bar.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the SeekBar layout", e);
        }
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!showingPlayback) {
                requestSeek();
            }
        });
        seekCooldown.setOnFinished(e -> {
            if (!Double.isNaN(pendingSeek)) {
                double fraction = pendingSeek;
                pendingSeek = Double.NaN;
                seekAndStartCooldown(fraction);
            }
        });
    }

    public void setOnSeekToFraction(DoubleConsumer onSeekToFraction) {
        this.onSeekToFraction = onSeekToFraction;
    }

    public void showPlayback(Duration current, Duration total) {
        if (!canFollowPlayback(current, total)) {
            return;
        }
        followPlaybackTo(current.toMillis() / total.toMillis() * slider.getMax());
        lblCurrentTime.setText(format(current));
        lblTotalTime.setText(format(total));
    }

    public void rewindToStart() {
        if (slider.isValueChanging()) {
            return;
        }
        cancelPendingSeek();
        followPlaybackTo(0);
        lblCurrentTime.setText(ZERO_TIME);
    }

    public void showNoTrack() {
        cancelPendingSeek();
        followPlaybackTo(0);
        lblCurrentTime.setText(ZERO_TIME);
        lblTotalTime.setText(ZERO_TIME);
    }

    public void setSeekEnabled(boolean enabled) {
        slider.setDisable(!enabled);
    }

    private boolean canFollowPlayback(Duration current, Duration total) {
        return !isUserSeeking() && current != null && total != null && !total.isUnknown() && total.toMillis() > 0;
    }

    private boolean isUserSeeking() {
        return slider.isValueChanging() || !Double.isNaN(pendingSeek);
    }

    private void followPlaybackTo(double value) {
        showingPlayback = true;
        try {
            slider.setValue(value);
        } finally {
            showingPlayback = false;
        }
    }

    private void requestSeek() {
        double fraction = slider.getValue() / slider.getMax();
        if (seekCooldown.getStatus() == Animation.Status.RUNNING) {
            pendingSeek = fraction;
        } else {
            seekAndStartCooldown(fraction);
        }
    }

    private void seekAndStartCooldown(double fraction) {
        onSeekToFraction.accept(fraction);
        seekCooldown.playFromStart();
    }

    private void cancelPendingSeek() {
        pendingSeek = Double.NaN;
        seekCooldown.stop();
    }

    static String format(Duration duration) {
        int totalSeconds = (int) duration.toSeconds();
        return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
    }
}
