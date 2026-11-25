package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.OutputStatus;
import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import java.util.concurrent.Executor;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

final class FakePlayback implements Playback {

    private final Executor executor;
    private final ReadOnlyObjectWrapper<PlaybackStatus> status = new ReadOnlyObjectWrapper<>(PlaybackStatus.UNKNOWN);
    private final ReadOnlyObjectWrapper<Duration> currentTime = new ReadOnlyObjectWrapper<>(Duration.ZERO);
    private final DoubleProperty volume = new SimpleDoubleProperty(1.0);
    private final ReadOnlyObjectWrapper<OutputStatus> output = new ReadOnlyObjectWrapper<>(OutputStatus.NONE);
    private Runnable onReady;
    private Runnable onEnd;
    private Runnable onError;
    private Duration total = Duration.UNKNOWN;
    private int spectrumAttachments;
    private java.util.List<String> calls;
    private String label = "";
    private boolean releaseResult = true;

    FakePlayback(Executor executor) {
        this.executor = executor;
    }

    void becomeReady() {
        status.set(PlaybackStatus.READY);
        postReady();
    }

    void endMedia() {
        if (onEnd != null) executor.execute(onEnd);
    }

    void fail() {
        status.set(PlaybackStatus.HALTED);
        if (onError != null) executor.execute(onError);
    }

    void setTotalDuration(Duration duration) {
        total = duration;
    }

    int spectrumAttachments() {
        return spectrumAttachments;
    }

    private void postReady() {
        if (onReady != null) executor.execute(onReady);
    }

    @Override
    public void play() {
        status.set(PlaybackStatus.PLAYING);
    }

    @Override
    public void pause() {
        status.set(PlaybackStatus.PAUSED);
    }

    @Override
    public void stop() {
        status.set(PlaybackStatus.STOPPED);
        currentTime.set(Duration.ZERO);
    }

    @Override
    public void dispose() {
        if (calls != null) calls.add(label + ".dispose");
        status.set(PlaybackStatus.DISPOSED);
    }

    void recordCalls(java.util.List<String> log, String name) {
        calls = log;
        label = name;
    }

    void releaseTakes(boolean released) {
        releaseResult = released;
    }

    @Override
    public boolean awaitRelease(long millis) {
        if (calls != null) calls.add(label + ".await<=" + millis);
        return releaseResult;
    }

    @Override
    public void seek(Duration time) {
        currentTime.set(time);
    }

    @Override
    public Duration getCurrentTime() {
        return currentTime.get();
    }

    @Override
    public Duration getTotalDuration() {
        return total;
    }

    @Override
    public ReadOnlyObjectProperty<Duration> currentTimeProperty() {
        return currentTime.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyObjectProperty<PlaybackStatus> statusProperty() {
        return status.getReadOnlyProperty();
    }

    @Override
    public PlaybackStatus getStatus() {
        return status.get();
    }

    void showOutput(OutputStatus status) {
        output.set(status);
    }

    @Override
    public ReadOnlyObjectProperty<OutputStatus> outputProperty() {
        return output.getReadOnlyProperty();
    }

    @Override
    public DoubleProperty volumeProperty() {
        return volume;
    }

    @Override
    public void setVolume(double value) {
        if (calls != null) calls.add(label + ".volume=" + value);
        volume.set(value);
    }

    @Override
    public double getVolume() {
        return volume.get();
    }

    @Override
    public void setOnReady(Runnable handler) {
        onReady = handler;
        if (status.get() == PlaybackStatus.READY) postReady();
    }

    @Override
    public void setOnEndOfMedia(Runnable handler) {
        onEnd = handler;
    }

    @Override
    public void setOnError(Runnable handler) {
        onError = handler;
    }

    @Override
    public Throwable getError() {
        return null;
    }

    @Override
    public void attachSpectrum(AudioAnalyzer analyzer) {
        spectrumAttachments++;
    }

    @Override
    public void detachSpectrum() {
        spectrumAttachments--;
    }
}
