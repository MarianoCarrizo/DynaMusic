package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

class WaveFormatTest {

    private static short shortAt(MemorySegment format, int offset) {
        return format.get(ValueLayout.JAVA_SHORT_UNALIGNED, offset);
    }

    private static int intAt(MemorySegment format, int offset) {
        return format.get(ValueLayout.JAVA_INT_UNALIGNED, offset);
    }

    @Test
    void theStructureIsFortyBytes() {
        try (Arena arena = Arena.ofConfined()) {
            assertEquals(40, WaveFormat.create(arena, SampleContainer.PADDED_32, 96_000, 2).byteSize());
        }
    }

    @Test
    void twentyFourInThirtyTwoAtNinetySixKilohertzStereo() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment format = WaveFormat.create(arena, SampleContainer.PADDED_32, 96_000, 2);

            assertEquals((short) 0xFFFE, shortAt(format, 0));
            assertEquals(2, shortAt(format, 2));
            assertEquals(96_000, intAt(format, 4));
            assertEquals(768_000, intAt(format, 8));
            assertEquals(8, shortAt(format, 12));
            assertEquals(32, shortAt(format, 14));
            assertEquals(22, shortAt(format, 16));
            assertEquals(24, shortAt(format, 18));
            assertEquals(3, intAt(format, 20));
        }
    }

    @Test
    void packedTwentyFourDescribesThreeByteSamples() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment format = WaveFormat.create(arena, SampleContainer.PACKED_24, 44_100, 2);

            assertEquals(6, shortAt(format, 12));
            assertEquals(24, shortAt(format, 14));
            assertEquals(24, shortAt(format, 18));
            assertEquals(264_600, intAt(format, 8));
        }
    }

    @Test
    void sixteenBitDescribesTwoByteSamples() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment format = WaveFormat.create(arena, SampleContainer.PACKED_16, 44_100, 2);

            assertEquals(4, shortAt(format, 12));
            assertEquals(16, shortAt(format, 14));
            assertEquals(16, shortAt(format, 18));
        }
    }

    @Test
    void monoUsesTheFrontCenterMask() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment format = WaveFormat.create(arena, SampleContainer.PADDED_32, 48_000, 1);

            assertEquals(1, shortAt(format, 2));
            assertEquals(4, shortAt(format, 12));
            assertEquals(4, intAt(format, 20));
        }
    }

    @Test
    void floatStereoDescribesThirtyTwoBitIeeeSamples() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment format = WaveFormat.createFloat(arena, 96_000, 2);
            byte[] subFormat = new byte[16];
            MemorySegment.copy(format, 24, MemorySegment.ofArray(subFormat), 0, 16);

            assertEquals((short) 0xFFFE, shortAt(format, 0));
            assertEquals(2, shortAt(format, 2));
            assertEquals(96_000, intAt(format, 4));
            assertEquals(768_000, intAt(format, 8));
            assertEquals(8, shortAt(format, 12));
            assertEquals(32, shortAt(format, 14));
            assertEquals(32, shortAt(format, 18));
            assertEquals(3, intAt(format, 20));
            assertArrayEquals(WaveFormat.IEEE_FLOAT.toBytes(), subFormat);
        }
    }

    @Test
    void subFormatIsIntegerPcm() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment format = WaveFormat.create(arena, SampleContainer.PADDED_32, 48_000, 2);
            byte[] subFormat = new byte[16];
            MemorySegment.copy(format, 24, MemorySegment.ofArray(subFormat), 0, 16);

            assertArrayEquals(WaveFormat.PCM.toBytes(), subFormat);
        }
    }
}
