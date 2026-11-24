package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SampleDepthTest {

    private static int[] reduce(int... samples24) {
        byte[] in = PcmBytes.pack(SampleWidth.BITS_24, samples24);
        byte[] out = new byte[samples24.length * 2];
        int written = SampleDepth.reduceToSixteen(in, in.length, out);
        return PcmBytes.unpack(SampleWidth.BITS_16, out, written);
    }

    @Test
    void extremesStayInRangeWithoutSignFlip() {
        assertArrayEquals(new int[] {32767, -32768, 0}, reduce(0x7FFFFF, -0x800000, 0));
    }

    @Test
    void positiveAndNegativeValuesKeepTheirSign() {
        assertArrayEquals(new int[] {1, -1, 100, -100}, reduce(256, -256, 25_600, -25_600));
    }

    @Test
    void smallValuesRoundToTheNearestStep() {
        assertArrayEquals(new int[] {0, 1, 0, -1}, reduce(127, 128, -128, -129));
    }

    @Test
    void channelOrderIsPreserved() {
        assertArrayEquals(new int[] {1, 2, 3, 4}, reduce(256, 512, 768, 1024));
    }

    @Test
    void reportsTheNumberOfBytesProduced() {
        byte[] in = PcmBytes.pack(SampleWidth.BITS_24, 1, 2, 3, 4);
        byte[] out = new byte[8];

        assertEquals(8, SampleDepth.reduceToSixteen(in, in.length, out));
    }
}
