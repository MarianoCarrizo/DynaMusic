package com.marianocarrizo.dynamusic.audio;

public final class UnplayableAudioException extends RuntimeException {

    public UnplayableAudioException(String message) {
        super(message);
    }

    public UnplayableAudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
