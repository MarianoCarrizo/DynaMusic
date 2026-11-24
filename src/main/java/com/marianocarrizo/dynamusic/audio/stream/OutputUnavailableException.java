package com.marianocarrizo.dynamusic.audio.stream;

public final class OutputUnavailableException extends RuntimeException {

    public OutputUnavailableException(String message) {
        super(message);
    }

    public OutputUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
