package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.util.Duration;

public interface Playback {

    void play();

    void pause();

    void stop();

    void dispose();

    void seek(Duration time);

    Duration getCurrentTime();

    Duration getTotalDuration();

    ReadOnlyObjectProperty<Duration> currentTimeProperty();

    ReadOnlyObjectProperty<PlaybackStatus> statusProperty();

    PlaybackStatus getStatus();

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
