package com.marianocarrizo.dynamusic.audio.pcm;

import com.marianocarrizo.dynamusic.audio.AudioFormat;

import java.io.File;
import java.io.IOException;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;

public final class StandardGainMeasurer implements GainMeasurer {

    private final BooleanSupplier ffmpegAvailable;

    public StandardGainMeasurer(BooleanSupplier ffmpegAvailable) {
        this.ffmpegAvailable = ffmpegAvailable;
    }

    @Override
    public double ofWav(File wav) throws IOException {
        return AudioFileGain.measure(wav);
    }

    @Override
    public OptionalDouble ofNative(File source) throws InterruptedException {
        try {
            if (AudioFormat.WAV.matches(source) || AudioFormat.AIFF.matches(source)) {
                return OptionalDouble.of(AudioFileGain.measure(source));
            }
            if (!ffmpegAvailable.getAsBoolean()) return OptionalDouble.empty();
            return OptionalDouble.of(FfmpegGainProbe.measure(source));
        } catch (IOException unmeasurable) {
            return OptionalDouble.empty();
        }
    }
}
