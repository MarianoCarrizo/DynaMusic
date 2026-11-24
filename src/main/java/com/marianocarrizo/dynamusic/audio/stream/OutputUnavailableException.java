package com.marianocarrizo.dynamusic.audio.stream;

public final class OutputUnavailableException extends RuntimeException {

    private final int consumedBytes;

    public OutputUnavailableException(String message) {
        super(message);
        this.consumedBytes = 0;
    }

    public OutputUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.consumedBytes = 0;
    }

    private OutputUnavailableException(OutputUnavailableException cause, int consumedBytes) {
        super(cause.getMessage(), cause);
        this.consumedBytes = consumedBytes;
    }

    public OutputUnavailableException consuming(int bytes) {
        return new OutputUnavailableException(this, bytes);
    }

    public int consumedBytes() {
        return consumedBytes;
    }
}
