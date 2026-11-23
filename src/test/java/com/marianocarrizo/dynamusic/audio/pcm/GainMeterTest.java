package com.marianocarrizo.dynamusic.audio.pcm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GainMeterTest {

    private static final double TOLERANCE = 1e-6;

    private static byte[] samples(int bits, boolean bigEndian, long... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bytes = bits / 8;
        for (long value : values) {
            for (int i = 0; i < bytes; i++) {
                int shift = 8 * (bigEndian ? bytes - 1 - i : i);
                out.write((int) (value >> shift) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private static double gainOf(GainMeter meter, byte[] data) {
        meter.accept(data, data.length);
        return meter.gain();
    }

    @Test
    void sixteenBitLittleEndianMatchesTheReferenceRms() {
        long amplitude = 3277;
        byte[] data = samples(16, false, amplitude, -amplitude, amplitude, -amplitude);

        double expected = 0.1 / (amplitude / 32768.0);

        assertEquals(Math.min(expected, 1.0), gainOf(new GainMeter(16, true, false), data), TOLERANCE);
    }

    @Test
    void bigEndianAndLittleEndianAgree() {
        long[] values = {10000, -20000, 5000, -7000};

        double little = gainOf(new GainMeter(16, true, false), samples(16, false, values));
        double big = gainOf(new GainMeter(16, true, true), samples(16, true, values));

        assertEquals(little, big, TOLERANCE);
    }

    @Test
    void twentyFourBitScalesLikeSixteenBitAtTheSameLevel() {
        double sixteen = gainOf(new GainMeter(16, true, false), samples(16, false, 8192, -8192));
        double twentyFour = gainOf(new GainMeter(24, true, false), samples(24, false, 8192L << 8, -(8192L << 8)));

        assertEquals(sixteen, twentyFour, TOLERANCE);
    }

    @Test
    void unsignedEightBitIsCenteredOnOneTwentyEight() {
        byte[] data = {(byte) 128, (byte) 128, (byte) 128, (byte) 128};

        assertEquals(1.0, gainOf(new GainMeter(8, false, false), data), TOLERANCE);
        double loud = gainOf(new GainMeter(8, false, false), new byte[] {(byte) 255, 0, (byte) 255, 0});
        assertEquals(0.1, loud, 0.01);
    }

    @Test
    void signedEightBitIsSupported() {
        assertEquals(0.1, gainOf(new GainMeter(8, true, false), new byte[] {127, -128, 127, -128}), 0.01);
    }

    @Test
    void silenceAndEmptyInputGiveUnityGain() {
        assertEquals(1.0, gainOf(new GainMeter(16, true, false), new byte[64]), TOLERANCE);
        assertEquals(1.0, new GainMeter(16, true, false).gain(), TOLERANCE);
    }

    @Test
    void quietSignalsAreClampedAtUnity() {
        assertEquals(1.0, gainOf(new GainMeter(16, true, false), samples(16, false, 10, -10, 10, -10)), TOLERANCE);
    }

    @Test
    void samplesSplitAcrossBuffersAreReassembled() {
        byte[] data = samples(24, false, 4000000, -4000000, 4000000, -4000000);
        GainMeter split = new GainMeter(24, true, false);
        for (int i = 0; i < data.length; i += 5) {
            byte[] part = Arrays.copyOfRange(data, i, Math.min(i + 5, data.length));
            split.accept(part, part.length);
        }

        assertEquals(gainOf(new GainMeter(24, true, false), data), split.gain(), TOLERANCE);
    }

    @Test
    void unsupportedSampleSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GainMeter(12, true, false));
        assertThrows(IllegalArgumentException.class, () -> new GainMeter(64, true, false));
    }
}
