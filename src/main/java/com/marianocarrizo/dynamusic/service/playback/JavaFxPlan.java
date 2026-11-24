package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.PreparedAudio;
import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import java.io.File;
import java.util.Optional;

public record JavaFxPlan(PreparedAudio prepared, OutputArbiter arbiter) implements PlaybackPlan {

    @Override
    public Playback open() {
        return JavaFxPlayback.open(prepared.playable(), arbiter);
    }

    @Override
    public double gain() {
        return prepared.gain();
    }

    @Override
    public Optional<File> held() {
        return Optional.of(prepared.playable());
    }

    @Override
    public void discard() {
    }
}
