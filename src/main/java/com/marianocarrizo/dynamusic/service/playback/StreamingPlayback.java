package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.PcmOutput;
import com.marianocarrizo.dynamusic.audio.stream.PcmSink;
import com.marianocarrizo.dynamusic.audio.stream.PcmSource;
import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import java.util.concurrent.Executor;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

public final class StreamingPlayback implements Playback {

    static final String THREAD_NAME = "stream-playback";
    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY + 2;
    private static final double DEFAULT_VOLUME = 1.0;

    private final Executor fx;
    private final StreamWorker worker;
    private final Thread thread;
    private final double sourceRate;
    private final long totalFrames;
    private final ReadOnlyObjectWrapper<PlaybackStatus> status = new ReadOnlyObjectWrapper<>(PlaybackStatus.READY);
    private final ReadOnlyObjectWrapper<Duration> currentTime = new ReadOnlyObjectWrapper<>(Duration.ZERO);
    private final DoubleProperty volume = new SimpleDoubleProperty(DEFAULT_VOLUME);
    private volatile int generation;
    private volatile Throwable error;
    private Runnable onReady;
    private Runnable onEnd;
    private Runnable onError;
    private boolean readyDelivered;

    public StreamingPlayback(PcmSource source, PcmSink sink, Executor fx) {
        this.fx = fx;
        this.sourceRate = source.format().getSampleRate();
        this.totalFrames = source.totalFrames();
        PcmOutput output = new PcmOutput(sink, source.format(), sourceRate, DEFAULT_VOLUME);
        this.worker = new StreamWorker(source, output, new Bridge());
        this.thread = new Thread(worker, THREAD_NAME);
        volume.addListener((observable, previous, next) -> worker.setVolume(next.doubleValue()));
        thread.setDaemon(true);
        thread.setPriority(THREAD_PRIORITY);
        fx.execute(this::deliverReady);
        thread.start();
    }

    boolean awaitTermination(long millis) throws InterruptedException {
        thread.join(millis);
        return !thread.isAlive();
    }

    @Override
    public void play() {
        if (!isUsable()) return;
        status.set(PlaybackStatus.PLAYING);
        worker.play();
    }

    @Override
    public void pause() {
        if (status.get() != PlaybackStatus.PLAYING) return;
        status.set(PlaybackStatus.PAUSED);
        worker.pause();
    }

    @Override
    public void stop() {
        if (!isUsable()) return;
        generation++;
        status.set(PlaybackStatus.STOPPED);
        currentTime.set(Duration.ZERO);
        worker.stopAtStart(generation);
    }

    @Override
    public void dispose() {
        if (status.get() == PlaybackStatus.DISPOSED) return;
        status.set(PlaybackStatus.DISPOSED);
        worker.close();
    }

    @Override
    public void seek(Duration time) {
        if (!isUsable() || time.isUnknown() || time.isIndefinite()) return;
        double seconds = Math.max(0, time.toSeconds());
        if (totalFrames > 0) seconds = Math.min(seconds, totalFrames / sourceRate);
        generation++;
        currentTime.set(Duration.seconds(seconds));
        worker.seek(Math.round(seconds * sourceRate), generation);
    }

    @Override
    public Duration getCurrentTime() {
        return currentTime.get();
    }

    @Override
    public Duration getTotalDuration() {
        return totalFrames > 0 ? Duration.seconds(totalFrames / sourceRate) : Duration.UNKNOWN;
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

    @Override
    public DoubleProperty volumeProperty() {
        return volume;
    }

    @Override
    public void setVolume(double value) {
        volume.set(value);
    }

    @Override
    public double getVolume() {
        return volume.get();
    }

    @Override
    public void setOnReady(Runnable handler) {
        onReady = handler;
        if (readyDelivered && handler != null) fx.execute(handler);
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
        return error;
    }

    @Override
    public void attachSpectrum(AudioAnalyzer analyzer) {
    }

    @Override
    public void detachSpectrum() {
    }

    private boolean isUsable() {
        PlaybackStatus current = status.get();
        return current != PlaybackStatus.DISPOSED && current != PlaybackStatus.HALTED;
    }

    private void deliverReady() {
        readyDelivered = true;
        if (onReady != null) onReady.run();
    }

    private final class Bridge implements StreamListener {

        @Override
        public void positionChanged(double seconds, int positionGeneration) {
            fx.execute(() -> {
                if (positionGeneration == generation) currentTime.set(Duration.seconds(seconds));
            });
        }

        @Override
        public void ended(int endGeneration) {
            fx.execute(() -> {
                if (endGeneration == generation && isUsable() && onEnd != null) onEnd.run();
            });
        }

        @Override
        public void failed(Throwable failure) {
            fx.execute(() -> {
                if (status.get() == PlaybackStatus.DISPOSED) return;
                error = failure;
                status.set(PlaybackStatus.HALTED);
                if (onError != null) onError.run();
            });
        }
    }
}
