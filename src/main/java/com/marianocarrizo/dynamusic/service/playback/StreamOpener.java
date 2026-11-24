package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import java.io.File;
import java.util.Optional;

@FunctionalInterface
public interface StreamOpener {

    Optional<PcmStream> open(File file);
}
