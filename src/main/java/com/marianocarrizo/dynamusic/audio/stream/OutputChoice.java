package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

public record OutputChoice(OutputPlan plan, AudioFormat sinkFormat) {

    public static OutputChoice javaFx() {
        return new OutputChoice(OutputPlan.JAVAFX, null);
    }
}
