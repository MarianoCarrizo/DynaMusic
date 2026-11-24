package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class WaveFormat {

    static final int SIZE = 40;
    static final int TAG_OFFSET = 0;
    static final int CHANNELS_OFFSET = 2;
    static final int SAMPLE_RATE_OFFSET = 4;
    static final int BYTES_PER_SECOND_OFFSET = 8;
    static final int BLOCK_ALIGN_OFFSET = 12;
    static final int CONTAINER_BITS_OFFSET = 14;
    static final int EXTENSION_SIZE_OFFSET = 16;
    static final int VALID_BITS_OFFSET = 18;
    static final int CHANNEL_MASK_OFFSET = 20;
    static final int SUB_FORMAT_OFFSET = 24;
    static final short EXTENSIBLE_TAG = (short) 0xFFFE;
    static final short EXTENSION_SIZE = 22;
    static final int MASK_FRONT_CENTER = 0x4;
    static final int MASK_FRONT_PAIR = 0x3;
    static final Guid PCM = new Guid("00000001-0000-0010-8000-00aa00389b71");
    static final Guid IEEE_FLOAT = new Guid("00000003-0000-0010-8000-00aa00389b71");

    private static final int BITS_PER_BYTE = 8;
    private static final int MONO = 1;
    private static final int FLOAT_BITS = 32;

    private WaveFormat() {
    }

    static MemorySegment create(Arena arena, SampleContainer container, int sampleRate, int channels) {
        return build(arena, container.containerBits(), container.validBits(), sampleRate, channels, PCM);
    }

    static MemorySegment createFloat(Arena arena, int sampleRate, int channels) {
        return build(arena, FLOAT_BITS, FLOAT_BITS, sampleRate, channels, IEEE_FLOAT);
    }

    private static MemorySegment build(Arena arena, int containerBits, int validBits, int sampleRate, int channels,
            Guid subFormat) {
        MemorySegment format = arena.allocate(SIZE, Long.BYTES);
        int blockAlign = channels * containerBits / BITS_PER_BYTE;
        format.set(ValueLayout.JAVA_SHORT_UNALIGNED, TAG_OFFSET, EXTENSIBLE_TAG);
        format.set(ValueLayout.JAVA_SHORT_UNALIGNED, CHANNELS_OFFSET, (short) channels);
        format.set(ValueLayout.JAVA_INT_UNALIGNED, SAMPLE_RATE_OFFSET, sampleRate);
        format.set(ValueLayout.JAVA_INT_UNALIGNED, BYTES_PER_SECOND_OFFSET, sampleRate * blockAlign);
        format.set(ValueLayout.JAVA_SHORT_UNALIGNED, BLOCK_ALIGN_OFFSET, (short) blockAlign);
        format.set(ValueLayout.JAVA_SHORT_UNALIGNED, CONTAINER_BITS_OFFSET, (short) containerBits);
        format.set(ValueLayout.JAVA_SHORT_UNALIGNED, EXTENSION_SIZE_OFFSET, EXTENSION_SIZE);
        format.set(ValueLayout.JAVA_SHORT_UNALIGNED, VALID_BITS_OFFSET, (short) validBits);
        format.set(ValueLayout.JAVA_INT_UNALIGNED, CHANNEL_MASK_OFFSET, channelMask(channels));
        subFormat.writeTo(format, SUB_FORMAT_OFFSET);
        return format;
    }

    static int channelMask(int channels) {
        return channels == MONO ? MASK_FRONT_CENTER : MASK_FRONT_PAIR;
    }
}
