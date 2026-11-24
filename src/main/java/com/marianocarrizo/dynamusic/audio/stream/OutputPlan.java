package com.marianocarrizo.dynamusic.audio.stream;

public enum OutputPlan {
    EXCLUSIVE(true),
    EXACT(true),
    REDUCED_DEPTH(true),
    RESAMPLED(true),
    JAVAFX(false);

    private final boolean streams;

    OutputPlan(boolean streams) {
        this.streams = streams;
    }

    public boolean streams() {
        return streams;
    }
}
