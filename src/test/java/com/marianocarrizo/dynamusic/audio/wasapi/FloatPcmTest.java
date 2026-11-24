package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class FloatPcmTest {

    private static float[] floats(byte[] bytes) {
        float[] values = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return values;
    }

    private static float[] convert(int bytesPerSample, int channels, int... samples) {
        byte[] source = PcmFrames.pack(bytesPerSample, samples);
        int frames = samples.length / channels;
        byte[] target = new byte[frames * FloatPcm.FRAME_BYTES];
        assertEquals(target.length, FloatPcm.convert(source, 0, bytesPerSample * 8, channels, frames, target));
        return floats(target);
    }

    @Test
    void sixteenBitSamplesDivideByTwoToTheFifteenth() {
        float[] converted = convert(2, 2, 16384, -16384, 1, -1);

        assertEquals(0.5f, converted[0]);
        assertEquals(-0.5f, converted[1]);
        assertEquals(1f / 32768f, converted[2]);
        assertEquals(-1f / 32768f, converted[3]);
    }

    @Test
    void fullScaleNegativeIsExactlyMinusOne() {
        assertEquals(-1f, convert(2, 2, -32768, 0)[0]);
        assertEquals(-1f, convert(3, 2, -8_388_608, 0)[0]);
    }

    @Test
    void fullScalePositiveStaysBelowOne() {
        assertEquals(32767f / 32768f, convert(2, 2, 32767, 0)[0]);
        assertEquals(8_388_607f / 8_388_608f, convert(3, 2, 8_388_607, 0)[0]);
    }

    @Test
    void everyTwentyFourBitSampleSurvivesTheRoundTripExactly() {
        int[] samples = {0, 1, -1, 255, -256, 65_535, -65_536, 8_388_607, -8_388_608, 4_194_305, -5_000_001};
        float[] converted = convert(3, 1, samples);

        for (int i = 0; i < samples.length; i++) {
            assertEquals(samples[i], (int) (converted[i * 2] * 8_388_608f), "sample " + i);
            assertEquals(converted[i * 2], converted[i * 2 + 1]);
        }
    }

    @Test
    void monoFramesAreDuplicatedOnBothChannels() {
        float[] converted = convert(2, 1, 8192, -8192);

        assertEquals(0.25f, converted[0]);
        assertEquals(0.25f, converted[1]);
        assertEquals(-0.25f, converted[2]);
        assertEquals(-0.25f, converted[3]);
    }

    @Test
    void stereoKeepsEachChannelOnItsOwnSide() {
        float[] converted = convert(3, 2, 4_194_304, -2_097_152);

        assertEquals(0.5f, converted[0]);
        assertEquals(-0.25f, converted[1]);
    }

    @Test
    void conversionHonoursTheSourceOffset() {
        byte[] source = PcmFrames.pack(2, 0, 0, 16384, 16384);
        byte[] target = new byte[FloatPcm.FRAME_BYTES];

        FloatPcm.convert(source, 4, 16, 2, 1, target);

        assertEquals(0.5f, floats(target)[0]);
        assertEquals(0.5f, floats(target)[1]);
    }

    @Test
    void onlySixteenAndTwentyFourBitMonoOrStereoAreSupported() {
        assertTrue(FloatPcm.isSourceSupported(16, 1));
        assertTrue(FloatPcm.isSourceSupported(24, 2));
        assertFalse(FloatPcm.isSourceSupported(32, 2));
        assertFalse(FloatPcm.isSourceSupported(8, 2));
        assertFalse(FloatPcm.isSourceSupported(24, 6));
        assertFalse(FloatPcm.isSourceSupported(24, 0));
    }
}
