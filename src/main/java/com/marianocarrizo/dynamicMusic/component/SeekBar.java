package com.marianocarrizo.dynamicMusic.component;

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

/**
 * Elapsed time, seek slider and total time of a player, as one reusable control.
 * Seeks as soon as the user moves the slider: pressing the track, dragging the thumb or using
 * the keyboard. While the user holds the slider it stops following playback.
 * Bursts of seeks are limited to one every {@link #SEEK_INTERVAL}, keeping only the
 * latest target, so the player never queues them up.
 */
public final class SeekBar extends HBox {

    private static final String ZERO_TIME = "0:00";
    private static final Duration SEEK_INTERVAL = Duration.millis(150);

    @FXML private Label lblCurrentTime;
    @FXML private Slider slider;
    @FXML private Label lblTotalTime;

    private DoubleConsumer onSeek = fraction -> { };
    private final PauseTransition seekCooldown = new PauseTransition(SEEK_INTERVAL);
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
                seek(fraction);
            }
        });
    }

    /** Receives the target position as a fraction of the track, from 0 to 1. */
    public void setOnSeek(DoubleConsumer onSeek) {
        this.onSeek = onSeek;
    }

    /** Shows the playback position, unless the user is moving the slider or the length is not known yet. */
    public void update(Duration current, Duration total) {
        if (isUserSeeking() || current == null || total == null || total.isUnknown() || total.toMillis() <= 0) {
            return;
        }
        showPosition(current.toMillis() / total.toMillis() * slider.getMax());
        lblCurrentTime.setText(format(current));
        lblTotalTime.setText(format(total));
    }

    /** Moves back to the start and keeps the total time. */
    public void rewind() {
        if (slider.isValueChanging()) {
            return;
        }
        cancelPendingSeek();
        showPosition(0);
        lblCurrentTime.setText(ZERO_TIME);
    }

    /** Clears position and total time, for when no track is loaded. */
    public void reset() {
        cancelPendingSeek();
        showPosition(0);
        lblCurrentTime.setText(ZERO_TIME);
        lblTotalTime.setText(ZERO_TIME);
    }

    /** Enables or disables seeking without dimming the time labels. */
    public void setSeekable(boolean seekable) {
        slider.setDisable(!seekable);
    }

    private boolean isUserSeeking() {
        return slider.isValueChanging() || !Double.isNaN(pendingSeek);
    }

    /** Moves the slider to follow playback without treating the change as a seek. */
    private void showPosition(double value) {
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
            seek(fraction);
        }
    }

    /** Seeks now and holds later requests until the player has had time to settle. */
    private void seek(double fraction) {
        onSeek.accept(fraction);
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
