package com.marianocarrizo.dynamusic.audio.stream;

import java.util.Optional;

record FlacFrameHeader(long firstSample, int length) {

    static final int MAX_BYTES = 16;
    private static final int SYNC_FIRST = 0xFF;
    private static final int SYNC_SECOND_FIXED = 0xF8;
    private static final int SYNC_SECOND_VARIABLE = 0xF9;
    private static final int PREFIX_BYTES = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final int NIBBLE_MASK = 0x0F;
    private static final int NIBBLE_BITS = 4;
    private static final int MAX_CHANNEL_ASSIGNMENT = 10;
    private static final int RESERVED_SAMPLE_SIZE = 3;
    private static final int SAMPLE_SIZE_MASK = 0x07;
    private static final int RESERVED_BIT = 0x01;
    private static final int CONTINUATION_MARK = 0x80;
    private static final int CONTINUATION_MASK = 0xC0;
    private static final int CONTINUATION_VALUE_BITS = 6;
    private static final int CONTINUATION_VALUE_MASK = 0x3F;
    private static final int BLOCK_SIZE_RESERVED = 0;
    private static final int MAX_CONTINUATION_BYTES = 6;
    private static final int BLOCK_SIZE_8_BIT = 6;
    private static final int BLOCK_SIZE_16_BIT = 7;
    private static final int RATE_8_BIT = 12;
    private static final int RATE_16_BIT_KHZ = 13;
    private static final int RATE_16_BIT_HZ = 14;
    private static final int RATE_INVALID = 15;

    static Optional<FlacFrameHeader> parse(byte[] data, int position, int fixedBlockSize) {
        if (position < 0 || position + PREFIX_BYTES >= data.length || !hasValidPrefix(data, position)) {
            return Optional.empty();
        }
        boolean variable = (data[position + 1] & RESERVED_BIT) != 0;
        int cursor = position + PREFIX_BYTES;
        int continuation = continuationBytes(data[cursor] & BYTE_MASK);
        if (continuation < 0 || cursor + continuation >= data.length) return Optional.empty();
        long number = numberAt(data, cursor, continuation);
        if (number < 0) return Optional.empty();
        cursor += continuation + 1;
        int blockCode = (data[position + 2] >> NIBBLE_BITS) & NIBBLE_MASK;
        int rateCode = data[position + 2] & NIBBLE_MASK;
        int blockExtra = blockSizeExtraBytes(blockCode);
        int rateExtra = rateExtraBytes(rateCode);
        if (blockExtra < 0 || rateExtra < 0 || cursor + blockExtra + rateExtra >= data.length) {
            return Optional.empty();
        }
        cursor += blockExtra + rateExtra;
        if (Crc8.compute(data, position, cursor) != (data[cursor] & BYTE_MASK)) return Optional.empty();
        long firstSample = variable ? number : number * fixedBlockSize;
        return Optional.of(new FlacFrameHeader(firstSample, cursor + 1 - position));
    }

    private static boolean hasValidPrefix(byte[] data, int position) {
        int second = data[position + 1] & BYTE_MASK;
        boolean sync = (data[position] & BYTE_MASK) == SYNC_FIRST
                && (second == SYNC_SECOND_FIXED || second == SYNC_SECOND_VARIABLE);
        if (!sync) return false;
        int layout = data[position + 3] & BYTE_MASK;
        int channelAssignment = layout >> NIBBLE_BITS;
        int sampleSize = (layout >> 1) & SAMPLE_SIZE_MASK;
        return channelAssignment <= MAX_CHANNEL_ASSIGNMENT && sampleSize != RESERVED_SAMPLE_SIZE
                && (layout & RESERVED_BIT) == 0;
    }

    private static int continuationBytes(int lead) {
        if (lead < CONTINUATION_MARK) return 0;
        if (lead < CONTINUATION_MASK) return -1;
        int count = 0;
        int mask = CONTINUATION_MARK;
        while ((lead & mask) != 0) {
            count++;
            mask >>= 1;
        }
        return count - 1 > MAX_CONTINUATION_BYTES ? -1 : count - 1;
    }

    private static long numberAt(byte[] data, int cursor, int continuation) {
        int lead = data[cursor] & BYTE_MASK;
        long value = continuation == 0 ? lead : lead & (CONTINUATION_VALUE_MASK >> continuation);
        for (int i = 1; i <= continuation; i++) {
            int next = data[cursor + i] & BYTE_MASK;
            if ((next & CONTINUATION_MASK) != CONTINUATION_MARK) return -1;
            value = (value << CONTINUATION_VALUE_BITS) | (next & CONTINUATION_VALUE_MASK);
        }
        return value;
    }

    private static int blockSizeExtraBytes(int code) {
        if (code == BLOCK_SIZE_RESERVED) return -1;
        if (code == BLOCK_SIZE_8_BIT) return 1;
        return code == BLOCK_SIZE_16_BIT ? 2 : 0;
    }

    private static int rateExtraBytes(int code) {
        if (code == RATE_INVALID) return -1;
        if (code == RATE_8_BIT) return 1;
        return code == RATE_16_BIT_KHZ || code == RATE_16_BIT_HZ ? 2 : 0;
    }
}
