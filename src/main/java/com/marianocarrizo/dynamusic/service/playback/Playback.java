package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.OutputStatus;
import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.util.Duration;

public interface Playback {

    void play();

    void pause();

    void stop();

    void dispose();

    default boolean awaitRelease(long millis) {
        return true;
    }

    void seek(Duration time);

    Duration getCurrentTime();

    Duration getTotalDuration();

    ReadOnlyObjectProperty<Duration> currentTimeProperty();

    ReadOnlyObjectProperty<PlaybackStatus> statusProperty();

    PlaybackStatus getStatus();

    ReadOnlyObjectProperty<OutputStatus> outputProperty();

    DoubleProperty volumeProperty();

    void setVolume(double volume);

    double getVolume();

    void setOnReady(Runnable handler);

    void setOnEndOfMedia(Runnable handler);

    void setOnError(Runnable handler);

    Throwable getError();

    void attachSpectrum(AudioAnalyzer analyzer);

    void detachSpectrum();
}
