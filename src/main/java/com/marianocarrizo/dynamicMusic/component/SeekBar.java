package com.marianocarrizo.dynamicMusic.component;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.DoubleConsumer;

/**
 * Elapsed time, seek slider and total time of a player, as one reusable control.
 * The slider stops following playback while the user drags it or holds an arrow key,
 * and seeks when the mouse button or the key is released.
 */
public final class SeekBar extends HBox {

    private static final String ZERO_TIME = "0:00";
    private static final Set<KeyCode> SEEK_KEYS = EnumSet.of(
            KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN, KeyCode.HOME, KeyCode.END);

    @FXML private Label lblCurrentTime;
    @FXML private Slider slider;
    @FXML private Label lblTotalTime;

    private boolean dragging = false;
    private DoubleConsumer onSeek = fraction -> { };

    public SeekBar() {
        FXMLLoader loader = new FXMLLoader(SeekBar.class.getResource("/views/component/seek_bar.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the SeekBar layout", e);
        }
        slider.setOnMousePressed(e -> dragging = true);
        slider.setOnMouseReleased(e -> finishSeeking());
        slider.setOnKeyPressed(e -> {
            if (SEEK_KEYS.contains(e.getCode())) {
                dragging = true;
            }
        });
        slider.setOnKeyReleased(e -> {
            if (SEEK_KEYS.contains(e.getCode())) {
                finishSeeking();
            }
        });
    }

    private void finishSeeking() {
        dragging = false;
        onSeek.accept(slider.getValue() / slider.getMax());
    }

    /** Receives the target position as a fraction of the track, from 0 to 1. */
    public void setOnSeek(DoubleConsumer onSeek) {
        this.onSeek = onSeek;
    }

    /** Shows the playback position, unless the user is dragging or the length is not known yet. */
    public void update(Duration current, Duration total) {
        if (dragging || current == null || total == null || total.isUnknown() || total.toMillis() <= 0) {
            return;
        }
        slider.setValue(current.toMillis() / total.toMillis() * slider.getMax());
        lblCurrentTime.setText(format(current));
        lblTotalTime.setText(format(total));
    }

    /** Moves back to the start and keeps the total time. */
    public void rewind() {
        if (dragging) {
            return;
        }
        slider.setValue(0);
        lblCurrentTime.setText(ZERO_TIME);
    }

    /** Clears position and total time, for when no track is loaded. */
    public void reset() {
        slider.setValue(0);
        lblCurrentTime.setText(ZERO_TIME);
        lblTotalTime.setText(ZERO_TIME);
    }

    /** Enables or disables seeking without dimming the time labels. */
    public void setSeekable(boolean seekable) {
        slider.setDisable(!seekable);
    }

    static String format(Duration duration) {
        int totalSeconds = (int) duration.toSeconds();
        return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
    }
}
