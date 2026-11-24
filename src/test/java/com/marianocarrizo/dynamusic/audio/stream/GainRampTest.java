package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GainRampTest {

    private static final double RATE = 44_100.0;
    private static final int RAMP_FRAMES = 441;
    private static final SampleWidth WIDTH = SampleWidth.BITS_16;

    private static byte[] constant(int value, int frames) {
        int[] samples = new int[frames * 2];
        Arrays.fill(samples, value);
        return PcmBytes.pack(WIDTH, samples);
    }

    private static int[] process(GainRamp ramp, byte[] block) {
        ramp.apply(block, 0, block.length, WIDTH, 2);
        return PcmBytes.unpack(WIDTH, block);
    }

    @Test
    void unityGainLeavesSamplesUntouched() {
        byte[] block = PcmBytes.pack(WIDTH, 100, -200, 300, -400);
        byte[] expected = block.clone();

        new GainRamp(RATE, 1.0).apply(block, 0, block.length, WIDTH, 2);

        assertArrayEquals(expected, block);
    }

    @Test
    void startingVolumeScalesOnlyByVolume() {
        int[] out = process(new GainRamp(RATE, 0.5), PcmBytes.pack(WIDTH, 1000, -2000, 3000, -4000));

        assertArrayEquals(new int[] {500, -1000, 1500, -2000}, out);
    }

    @Test
    void zeroVolumeSilencesTheBlock() {
        int[] out = process(new GainRamp(RATE, 0.0), constant(12_345, 10));

        assertTrue(Arrays.stream(out).allMatch(sample -> sample == 0));
    }

    @Test
    void changingTheTargetRampsOverTenMilliseconds() {
        GainRamp ramp = new GainRamp(RATE, 1.0);
        ramp.setTarget(0.0);

        int[] out = process(ramp, constant(10_000, RAMP_FRAMES * 2));

        assertTrue(out[0] > 9_900);
        assertTrue(out[RAMP_FRAMES] < 5_100 && out[RAMP_FRAMES] > 4_900);
        assertEquals(0, out[out.length - 1]);
        assertEquals(0, out[RAMP_FRAMES * 2]);
    }

    @Test
    void fadeAcrossBlocksIsMonotonicWithoutJumps() {
        GainRamp ramp = new GainRamp(RATE, 1.0);
        int previous = 10_000;
        int largestStep = 0;
        for (int step = 1; step <= 10; step++) {
            ramp.setTarget(1.0 - step / 10.0);
            int[] out = process(ramp, constant(10_000, RAMP_FRAMES));
            for (int i = 0; i < out.length; i += 2) {
                assertTrue(out[i] <= previous, "amplitude rose at step " + step);
                largestStep = Math.max(largestStep, previous - out[i]);
                previous = out[i];
            }
        }

        assertEquals(0, previous);
        assertTrue(largestStep <= 10_000 / 10 / RAMP_FRAMES + 2, "step " + largestStep);
    }

    @Test
    void restartingFromSilenceFadesTheNextBlockIn() {
        GainRamp ramp = new GainRamp(RATE, 1.0);
        ramp.restartFromSilence();

        int[] out = process(ramp, constant(10_000, RAMP_FRAMES * 2));

        assertEquals(0, out[0] / 100);
        assertEquals(10_000, out[out.length - 1]);
    }

    @Test
    void twentyFourBitSamplesScaleWithoutOverflow() {
        SampleWidth wide = SampleWidth.BITS_24;
        byte[] block = PcmBytes.pack(wide, 0x7FFFFF, -0x800000);
        new GainRamp(RATE, 0.5).apply(block, 0, block.length, wide, 2);

        assertArrayEquals(new int[] {0x400000, -0x400000}, PcmBytes.unpack(wide, block));
    }
}
