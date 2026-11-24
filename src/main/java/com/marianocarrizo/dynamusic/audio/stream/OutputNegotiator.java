package com.marianocarrizo.dynamusic.audio.stream;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.sound.sampled.AudioFormat;

public final class OutputNegotiator {

    private static final int REDUCED_BITS = 16;
    private static final int MAX_RESAMPLED_CHANNELS = 2;

    private final Predicate<AudioFormat> lineSupports;
    private final BooleanSupplier ffmpegAvailable;

    public OutputNegotiator(Predicate<AudioFormat> lineSupports, BooleanSupplier ffmpegAvailable) {
        this.lineSupports = lineSupports;
        this.ffmpegAvailable = ffmpegAvailable;
    }

    public OutputChoice negotiate(AudioFormat source) {
        if (lineSupports.test(source)) return new OutputChoice(OutputPlan.EXACT, source);
        OutputChoice reduced = reducedDepth(source);
        if (reduced != null) return reduced;
        OutputChoice resampled = resampled(source);
        return resampled != null ? resampled : OutputChoice.javaFx();
    }

    private OutputChoice reducedDepth(AudioFormat source) {
        if (source.getSampleSizeInBits() <= REDUCED_BITS) return null;
        AudioFormat reduced = PcmFormats.withBits(source, REDUCED_BITS);
        return lineSupports.test(reduced) ? new OutputChoice(OutputPlan.REDUCED_DEPTH, reduced) : null;
    }

    private OutputChoice resampled(AudioFormat source) {
        if (!ffmpegAvailable.getAsBoolean()) return null;
        int channels = Math.min(source.getChannels(), MAX_RESAMPLED_CHANNELS);
        for (ResampleTarget target : ResampleTarget.preferenceFor(source.getSampleRate())) {
            AudioFormat candidate = PcmFormats.signedLittleEndian(REDUCED_BITS, target.rate(), channels);
            if (lineSupports.test(candidate)) return new OutputChoice(OutputPlan.RESAMPLED, candidate);
        }
        return null;
    }
}
