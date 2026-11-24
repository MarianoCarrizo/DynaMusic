package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

public final class JavaSoundOpening {

    private static final int REDUCED_BITS = 16;

    private JavaSoundOpening() {
    }

    public static PcmSink open(SharedLines lines, AudioFormat source) {
        try {
            boolean reduces = source.getSampleSizeInBits() > REDUCED_BITS && !lines.supports(source);
            if (!reduces) return lines.open(source);
            return new ReducedDepthSink(source, lines.open(PcmFormats.withBits(source, REDUCED_BITS)));
        } catch (LineUnavailableException unavailable) {
            throw new OutputUnavailableException("No shared output is available", unavailable);
        }
    }
}
