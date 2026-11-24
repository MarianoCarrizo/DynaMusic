package com.marianocarrizo.dynamusic.component;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.DoubleConsumer;

public final class SeekBar extends HBox {

    private static final String ZERO_TIME = "0:00";
    private static final String THUMB_SELECTOR = ".thumb";

    @FXML private Label lblCurrentTime;
    @FXML private FilledSlider slider;
    @FXML private Label lblTotalTime;

    private DoubleConsumer onSeekToFraction = fraction -> { };
    private final SeekGesture gesture = new SeekGesture();
    private Duration shownTotal = Duration.UNKNOWN;
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
        slider.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPressed);
        slider.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        slider.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onReleased);
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!showingPlayback && !gesture.isDragging()) {
                seekTo(newValue.doubleValue() / slider.getMax());
            }
        });
    }

    public void setOnSeekToFraction(DoubleConsumer onSeekToFraction) {
        this.onSeekToFraction = onSeekToFraction;
    }

    public void showPlayback(Duration current, Duration total) {
        if (current == null || total == null || total.isUnknown() || total.toMillis() <= 0) {
            return;
        }
        shownTotal = total;
        lblTotalTime.setText(format(total));
        if (!gesture.accepts(current.toSeconds(), total.toSeconds(), System.currentTimeMillis())) {
            return;
        }
        followPlaybackTo(current.toMillis() / total.toMillis() * slider.getMax());
        lblCurrentTime.setText(format(current));
    }

    public void rewindToStart() {
        if (gesture.isDragging()) {
            return;
        }
        gesture.cancel();
        followPlaybackTo(0);
        lblCurrentTime.setText(ZERO_TIME);
    }

    public void showNoTrack() {
        gesture.cancel();
        shownTotal = Duration.UNKNOWN;
        followPlaybackTo(0);
        lblCurrentTime.setText(ZERO_TIME);
        lblTotalTime.setText(ZERO_TIME);
    }

    public void setSeekEnabled(boolean enabled) {
        if (!enabled) {
            gesture.cancel();
        }
        slider.setDisable(!enabled);
    }

    private void onPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        gesture.press(fractionOf(event));
        showTarget();
        event.consume();
    }

    private void onDragged(MouseEvent event) {
        if (!gesture.isDragging()) {
            return;
        }
        gesture.drag(fractionOf(event));
        showTarget();
        event.consume();
    }

    private void onReleased(MouseEvent event) {
        if (!gesture.isDragging()) {
            return;
        }
        double fraction = fractionOf(event);
        gesture.drag(fraction);
        showTarget();
        gesture.release(fraction, System.currentTimeMillis()).ifPresent(onSeekToFraction);
        event.consume();
    }

    private void seekTo(double fraction) {
        gesture.jump(fraction, System.currentTimeMillis()).ifPresent(onSeekToFraction);
        showTarget();
    }

    private double fractionOf(MouseEvent event) {
        Region thumb = (Region) slider.lookup(THUMB_SELECTOR);
        double thumbWidth = thumb == null ? 0 : thumb.getWidth();
        Insets insets = slider.getInsets();
        double start = insets.getLeft() + thumbWidth / 2;
        double length = slider.getWidth() - insets.getLeft() - insets.getRight() - thumbWidth;
        return SeekGesture.fractionAt(event.getX(), start, length);
    }

    private void showTarget() {
        double fraction = gesture.target();
        followPlaybackTo(fraction * slider.getMax());
        if (!shownTotal.isUnknown()) {
            lblCurrentTime.setText(format(shownTotal.multiply(fraction)));
        }
    }

    private void followPlaybackTo(double value) {
        showingPlayback = true;
        try {
            slider.setValue(value);
        } finally {
            showingPlayback = false;
        }
    }

    static String format(Duration duration) {
        int totalSeconds = (int) duration.toSeconds();
        return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
    }
}
