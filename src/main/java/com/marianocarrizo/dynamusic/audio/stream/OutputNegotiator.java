package com.marianocarrizo.dynamusic.audio.stream;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.sound.sampled.AudioFormat;

public final class OutputNegotiator {

    private static final int REDUCED_BITS = 16;
    private static final Set<Integer> EXCLUSIVE_DEPTHS = Set.of(16, 24);
    private static final int MAX_RESAMPLED_CHANNELS = 2;

    private final Predicate<AudioFormat> exclusiveSupports;
    private final Predicate<AudioFormat> wasapiSharedSupports;
    private final Predicate<AudioFormat> lineSupports;
    private final BooleanSupplier ffmpegAvailable;

    public OutputNegotiator(Predicate<AudioFormat> lineSupports, BooleanSupplier ffmpegAvailable) {
        this(format -> false, lineSupports, ffmpegAvailable);
    }

    public OutputNegotiator(Predicate<AudioFormat> exclusiveSupports, Predicate<AudioFormat> lineSupports,
            BooleanSupplier ffmpegAvailable) {
        this(exclusiveSupports, format -> false, lineSupports, ffmpegAvailable);
    }

    public OutputNegotiator(Predicate<AudioFormat> exclusiveSupports, Predicate<AudioFormat> wasapiSharedSupports,
            Predicate<AudioFormat> lineSupports, BooleanSupplier ffmpegAvailable) {
        this.exclusiveSupports = exclusiveSupports;
        this.wasapiSharedSupports = wasapiSharedSupports;
        this.lineSupports = lineSupports;
        this.ffmpegAvailable = ffmpegAvailable;
    }

    public OutputChoice negotiate(AudioFormat source) {
        if (exclusiveOffered(source)) return new OutputChoice(OutputPlan.EXCLUSIVE, source);
        if (wasapiSharedOffered(source)) return new OutputChoice(OutputPlan.WASAPI_SHARED, source);
        if (lineSupports.test(source)) return new OutputChoice(OutputPlan.EXACT, source);
        OutputChoice reduced = reducedDepth(source);
        if (reduced != null) return reduced;
        OutputChoice resampled = resampled(source);
        return resampled != null ? resampled : OutputChoice.javaFx();
    }

    private boolean exclusiveOffered(AudioFormat source) {
        return offered(source, exclusiveSupports);
    }

    private boolean wasapiSharedOffered(AudioFormat source) {
        return offered(source, wasapiSharedSupports);
    }

    private static boolean offered(AudioFormat source, Predicate<AudioFormat> device) {
        boolean supportedDepth = EXCLUSIVE_DEPTHS.contains(source.getSampleSizeInBits());
        return supportedDepth && source.getChannels() <= MAX_RESAMPLED_CHANNELS && device.test(source);
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
