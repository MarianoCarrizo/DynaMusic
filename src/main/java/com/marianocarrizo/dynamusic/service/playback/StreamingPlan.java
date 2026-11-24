package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.Executor;

public record StreamingPlan(PcmStream stream, Executor fx) implements PlaybackPlan {

    static final double UNITY_GAIN = 1.0;

    @Override
    public Playback open() {
        try {
            return new StreamingPlayback(stream.source(), stream.sink(), fx);
        } catch (RuntimeException failure) {
            stream.close();
            throw failure;
        }
    }

    @Override
    public double gain() {
        return UNITY_GAIN;
    }

    @Override
    public Optional<File> held() {
        return Optional.empty();
    }

    @Override
    public void discard() {
        stream.close();
    }
}
