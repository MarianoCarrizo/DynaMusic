package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import java.io.File;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

public final class JavaFxPlayback implements Playback {

    private static final double SPECTRUM_INTERVAL_SECONDS = 0.025;
    private static final int SPECTRUM_THRESHOLD_DB = -75;

    private final MediaPlayer player;
    private final ReadOnlyObjectWrapper<PlaybackStatus> status;

    private JavaFxPlayback(MediaPlayer player) {
        this.player = player;
        this.status = new ReadOnlyObjectWrapper<>(PlaybackStatus.from(player.getStatus()));
        player.statusProperty().addListener(
                (obs, oldStatus, newStatus) -> status.set(PlaybackStatus.from(newStatus)));
    }

    public static JavaFxPlayback open(File file) {
        return new JavaFxPlayback(new MediaPlayer(new Media(file.toURI().toString())));
    }

    @Override
    public void play() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void dispose() {
        player.dispose();
    }

    @Override
    public void seek(Duration time) {
        player.seek(time);
    }

    @Override
    public Duration getCurrentTime() {
        return player.getCurrentTime();
    }

    @Override
    public Duration getTotalDuration() {
        return player.getTotalDuration();
    }

    @Override
    public ReadOnlyObjectProperty<Duration> currentTimeProperty() {
        return player.currentTimeProperty();
    }

    @Override
    public ReadOnlyObjectProperty<PlaybackStatus> statusProperty() {
        return status.getReadOnlyProperty();
    }

    @Override
    public PlaybackStatus getStatus() {
        return PlaybackStatus.from(player.getStatus());
    }

    @Override
    public DoubleProperty volumeProperty() {
        return player.volumeProperty();
    }

    @Override
    public void setVolume(double volume) {
        player.setVolume(volume);
    }

    @Override
    public double getVolume() {
        return player.getVolume();
    }

    @Override
    public void setOnReady(Runnable handler) {
        player.setOnReady(handler);
    }

    @Override
    public void setOnEndOfMedia(Runnable handler) {
        player.setOnEndOfMedia(handler);
    }

    @Override
    public void setOnError(Runnable handler) {
        player.setOnError(handler);
    }

    @Override
    public Throwable getError() {
        return player.getError();
    }

    @Override
    public void attachSpectrum(AudioAnalyzer analyzer) {
        player.setAudioSpectrumInterval(SPECTRUM_INTERVAL_SECONDS);
        player.setAudioSpectrumNumBands(AudioAnalyzer.NUM_BANDS);
        player.setAudioSpectrumThreshold(SPECTRUM_THRESHOLD_DB);
        player.setAudioSpectrumListener(
                (timestamp, duration, magnitudes, phases) -> analyzer.process(magnitudes, timestamp));
    }

    @Override
    public void detachSpectrum() {
        player.setAudioSpectrumListener(null);
    }
}
