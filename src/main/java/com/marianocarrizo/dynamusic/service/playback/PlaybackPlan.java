package com.marianocarrizo.dynamusic.service.playback;

import java.io.File;
import java.util.Optional;

public sealed interface PlaybackPlan permits JavaFxPlan, StreamingPlan {

    Playback open();

    double gain();

    Optional<File> held();

    void discard();
}
