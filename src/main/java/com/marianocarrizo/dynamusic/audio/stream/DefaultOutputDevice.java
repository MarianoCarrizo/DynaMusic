package com.marianocarrizo.dynamusic.audio.stream;

import java.util.Optional;

@FunctionalInterface
public interface DefaultOutputDevice {

    Optional<String> currentId();

    static DefaultOutputDevice unavailable() {
        return Optional::empty;
    }
}
