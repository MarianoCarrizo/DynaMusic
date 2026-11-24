package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.PreparedAudio;
import java.io.File;
import java.util.Optional;

public record JavaFxPlan(PreparedAudio prepared) implements PlaybackPlan {

    @Override
    public Playback open() {
        return JavaFxPlayback.open(prepared.playable());
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
