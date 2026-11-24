package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SampleWidthTest {

    @Test
    void roundTripsSignedLittleEndianSamples() {
        for (SampleWidth width : SampleWidth.values()) {
            int[] samples = {width.min(), -1, 0, 1, width.max()};

            assertArrayEquals(samples, PcmBytes.unpack(width, PcmBytes.pack(width, samples)));
        }
    }

    @Test
    void writesLeastSignificantByteFirst() {
        assertArrayEquals(new byte[] {0x34, 0x12}, PcmBytes.pack(SampleWidth.BITS_16, 0x1234));
        assertArrayEquals(new byte[] {0x56, 0x34, 0x12}, PcmBytes.pack(SampleWidth.BITS_24, 0x123456));
    }

    @Test
    void clampsToTheRangeOfTheWidth() {
        assertEquals(32767, SampleWidth.BITS_16.clamp(100_000));
        assertEquals(-32768, SampleWidth.BITS_16.clamp(-100_000));
    }

    @Test
    void resolvesSupportedBitDepthsOnly() {
        assertEquals(SampleWidth.BITS_24, SampleWidth.ofBits(24));
        assertThrows(IllegalArgumentException.class, () -> SampleWidth.ofBits(32));
    }
}
