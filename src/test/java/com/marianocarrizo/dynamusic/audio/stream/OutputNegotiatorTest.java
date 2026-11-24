package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class OutputNegotiatorTest {

    private static final int MAX_CHANNELS = 2;
    private static final BooleanSupplier FFMPEG = () -> true;
    private static final BooleanSupplier NO_FFMPEG = () -> false;

    private static Predicate<AudioFormat> device(int maxBits, float maxRate) {
        return format -> format.getSampleSizeInBits() <= maxBits
                && format.getSampleRate() <= maxRate
                && format.getChannels() <= MAX_CHANNELS;
    }

    private static AudioFormat source(int bits, float rate, int channels) {
        return PcmFormats.signedLittleEndian(bits, rate, channels);
    }

    private static void assertFormat(AudioFormat actual, int bits, float rate, int channels) {
        assertEquals(bits, actual.getSampleSizeInBits());
        assertEquals(rate, actual.getSampleRate());
        assertEquals(channels, actual.getChannels());
    }

    @Test
    void sourceFormatIsUsedUnchangedWhenTheDeviceSupportsIt() {
        AudioFormat source = source(24, 96_000, 2);

        OutputChoice choice = new OutputNegotiator(device(24, 192_000), FFMPEG).negotiate(source);

        assertEquals(OutputPlan.EXACT, choice.plan());
        assertEquals(source, choice.sinkFormat());
    }

    @Test
    void sixteenBitSourceOpensExactlyWhenSupported() {
        OutputChoice choice = new OutputNegotiator(device(16, 48_000), FFMPEG).negotiate(source(16, 44_100, 2));

        assertEquals(OutputPlan.EXACT, choice.plan());
    }

    @Test
    void twentyFourBitDegradesToSixteenAtTheSameRate() {
        OutputChoice choice = new OutputNegotiator(device(16, 96_000), FFMPEG).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.REDUCED_DEPTH, choice.plan());
        assertFormat(choice.sinkFormat(), 16, 96_000, 2);
    }

    @Test
    void highRateResamplesToTheFamilyTargetThroughFfmpeg() {
        OutputChoice choice = new OutputNegotiator(device(16, 48_000), FFMPEG).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.RESAMPLED, choice.plan());
        assertFormat(choice.sinkFormat(), 16, 48_000, 2);
    }

    @Test
    void the44100FamilyResamplesTo44100() {
        OutputChoice choice = new OutputNegotiator(device(16, 48_000), FFMPEG).negotiate(source(24, 88_200, 2));

        assertEquals(OutputPlan.RESAMPLED, choice.plan());
        assertFormat(choice.sinkFormat(), 16, 44_100, 2);
    }

    @Test
    void sixteenBitSourceAboveTheDeviceRateIsResampled() {
        OutputChoice choice = new OutputNegotiator(device(16, 96_000), FFMPEG).negotiate(source(16, 192_000, 2));

        assertEquals(OutputPlan.RESAMPLED, choice.plan());
        assertFormat(choice.sinkFormat(), 16, 48_000, 2);
    }

    @Test
    void fallsBackToJavaFxWhenFfmpegIsUnavailable() {
        OutputChoice choice = new OutputNegotiator(device(16, 48_000), NO_FFMPEG).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.JAVAFX, choice.plan());
        assertNull(choice.sinkFormat());
    }

    @Test
    void surroundSourcesDownmixToStereoThroughFfmpeg() {
        OutputChoice choice = new OutputNegotiator(device(24, 96_000), FFMPEG).negotiate(source(24, 96_000, 6));

        assertEquals(OutputPlan.RESAMPLED, choice.plan());
        assertFormat(choice.sinkFormat(), 16, 48_000, 2);
    }

    @Test
    void surroundSourcesFallBackToJavaFxWithoutFfmpeg() {
        OutputChoice choice = new OutputNegotiator(device(24, 96_000), NO_FFMPEG).negotiate(source(24, 96_000, 6));

        assertEquals(OutputPlan.JAVAFX, choice.plan());
    }

    @Test
    void aDeviceThatSupportsNothingFallsBackToJavaFx() {
        OutputChoice choice = new OutputNegotiator(format -> false, FFMPEG).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.JAVAFX, choice.plan());
    }

    @Test
    void everyProbeOutcomeReturnsAChoiceWithoutThrowing() {
        int[] bitDepths = {16, 24};
        float[] rates = {44_100, 48_000, 96_000, 192_000};
        int[] channelCounts = {1, 2, 6};
        for (int maxBits : bitDepths) {
            for (float maxRate : rates) {
                for (BooleanSupplier ffmpeg : new BooleanSupplier[] {FFMPEG, NO_FFMPEG}) {
                    OutputNegotiator negotiator = new OutputNegotiator(device(maxBits, maxRate), ffmpeg);
                    for (int bits : bitDepths) {
                        for (float rate : rates) {
                            for (int channels : channelCounts) {
                                assertDoesNotThrow(() -> {
                                    OutputChoice choice = negotiator.negotiate(source(bits, rate, channels));
                                    assertTrue(choice.plan() == OutputPlan.JAVAFX || choice.sinkFormat() != null);
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    private static OutputNegotiator withExclusive(Predicate<AudioFormat> exclusive, Predicate<AudioFormat> line) {
        return new OutputNegotiator(exclusive, line, FFMPEG);
    }

    @Test
    void exclusiveIsOfferedBeforeTheExactFormat() {
        AudioFormat source = source(24, 96_000, 2);

        OutputChoice choice = withExclusive(format -> true, device(24, 192_000)).negotiate(source);

        assertEquals(OutputPlan.EXCLUSIVE, choice.plan());
        assertEquals(source, choice.sinkFormat());
    }

    @Test
    void aRejectedExclusiveFormatFallsToExact() {
        OutputChoice choice = withExclusive(format -> false, device(24, 192_000)).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.EXACT, choice.plan());
    }

    @Test
    void sixteenBitSourcesCanGoExclusive() {
        OutputChoice choice = withExclusive(format -> true, device(24, 48_000)).negotiate(source(16, 44_100, 2));

        assertEquals(OutputPlan.EXCLUSIVE, choice.plan());
        assertFormat(choice.sinkFormat(), 16, 44_100, 2);
    }

    @Test
    void anExclusiveRejectionDegradesThroughTheWholeLadderWithoutThrowing() {
        OutputChoice choice = withExclusive(format -> false, device(16, 48_000)).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.RESAMPLED, choice.plan());
    }

    @Test
    void unsupportedDepthsAndSurroundSourcesAreNeverProbedForExclusive() {
        OutputNegotiator negotiator = withExclusive(format -> {
            throw new AssertionError("probed " + format);
        }, device(32, 96_000));

        assertEquals(OutputPlan.EXACT, negotiator.negotiate(source(8, 44_100, 2)).plan());
        assertEquals(OutputPlan.EXACT, negotiator.negotiate(source(32, 44_100, 2)).plan());
        assertTrue(negotiator.negotiate(source(24, 96_000, 6)).plan() != OutputPlan.EXCLUSIVE);
    }

    private static OutputNegotiator withShared(Predicate<AudioFormat> exclusive, Predicate<AudioFormat> shared,
            Predicate<AudioFormat> line) {
        return new OutputNegotiator(exclusive, shared, line, FFMPEG);
    }

    @Test
    void wasapiSharedIsOfferedBeforeTheExactFormat() {
        AudioFormat source = source(24, 96_000, 2);

        OutputChoice choice = withShared(format -> false, format -> true, device(24, 192_000)).negotiate(source);

        assertEquals(OutputPlan.WASAPI_SHARED, choice.plan());
        assertEquals(source, choice.sinkFormat());
    }

    @Test
    void exclusiveStillWinsOverWasapiShared() {
        OutputChoice choice = withShared(format -> true, format -> true, device(24, 192_000))
                .negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.EXCLUSIVE, choice.plan());
    }

    @Test
    void aRejectedWasapiSharedFormatFallsToTheJavaSoundLadder() {
        Predicate<AudioFormat> none = format -> false;

        assertEquals(OutputPlan.EXACT,
                withShared(none, none, device(24, 192_000)).negotiate(source(24, 96_000, 2)).plan());
        assertEquals(OutputPlan.REDUCED_DEPTH,
                withShared(none, none, device(16, 192_000)).negotiate(source(24, 96_000, 2)).plan());
        assertEquals(OutputPlan.RESAMPLED,
                withShared(none, none, device(16, 48_000)).negotiate(source(24, 96_000, 2)).plan());
    }

    @Test
    void wasapiSharedCarriesSixteenAndTwentyFourBitMonoAndStereoSources() {
        OutputNegotiator negotiator = withShared(format -> false, format -> true, device(16, 48_000));

        assertEquals(OutputPlan.WASAPI_SHARED, negotiator.negotiate(source(16, 44_100, 2)).plan());
        assertEquals(OutputPlan.WASAPI_SHARED, negotiator.negotiate(source(24, 192_000, 1)).plan());
        assertEquals(OutputPlan.WASAPI_SHARED, negotiator.negotiate(source(24, 96_000, 2)).plan());
    }

    @Test
    void unsupportedDepthsAndSurroundSourcesAreNeverProbedForWasapiShared() {
        OutputNegotiator negotiator = withShared(format -> false, format -> {
            throw new AssertionError("probed " + format);
        }, device(32, 96_000));

        assertEquals(OutputPlan.EXACT, negotiator.negotiate(source(8, 44_100, 2)).plan());
        assertEquals(OutputPlan.EXACT, negotiator.negotiate(source(32, 44_100, 2)).plan());
        assertTrue(negotiator.negotiate(source(24, 96_000, 6)).plan() != OutputPlan.WASAPI_SHARED);
    }

    @Test
    void theThreeArgumentConstructorNeverOffersWasapiShared() {
        OutputChoice choice = new OutputNegotiator(format -> false, device(24, 96_000), FFMPEG)
                .negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.EXACT, choice.plan());
    }

    @Test
    void theTwoArgumentConstructorNeverOffersExclusive() {
        OutputChoice choice = new OutputNegotiator(device(24, 96_000), FFMPEG).negotiate(source(24, 96_000, 2));

        assertEquals(OutputPlan.EXACT, choice.plan());
    }
}
