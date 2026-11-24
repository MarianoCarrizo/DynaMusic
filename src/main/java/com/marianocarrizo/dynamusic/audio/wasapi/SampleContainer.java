package com.marianocarrizo.dynamusic.audio.wasapi;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public enum SampleContainer {
    PADDED_32(32, 24),
    PACKED_24(24, 24),
    PACKED_16(16, 16);

    private static final int BITS_PER_BYTE = 8;
    private static final int SIXTEEN = 16;
    private static final int TWENTY_FOUR = 24;

    private final int containerBits;
    private final int validBits;

    SampleContainer(int containerBits, int validBits) {
        this.containerBits = containerBits;
        this.validBits = validBits;
    }

    public static boolean isSourceSupported(int sourceBits) {
        return sourceBits == SIXTEEN || sourceBits == TWENTY_FOUR;
    }

    public static List<SampleContainer> candidates(int sourceBits) {
        return switch (sourceBits) {
            case TWENTY_FOUR -> List.of(PADDED_32, PACKED_24);
            case SIXTEEN -> List.of(PACKED_16, PADDED_32);
            default -> List.of();
        };
    }

    public static Optional<SampleContainer> choose(int sourceBits, Predicate<SampleContainer> accepted) {
        return candidates(sourceBits).stream().filter(accepted).findFirst();
    }

    public int containerBits() {
        return containerBits;
    }

    public int validBits() {
        return validBits;
    }

    public int bytes() {
        return containerBits / BITS_PER_BYTE;
    }

    public int repack(byte[] source, int sourceOffset, int sourceBits, int samples, byte[] target, int targetOffset) {
        if (!candidates(sourceBits).contains(this)) {
            throw new IllegalArgumentException(this + " cannot carry " + sourceBits + "-bit samples");
        }
        int sourceBytes = sourceBits / BITS_PER_BYTE;
        int width = bytes();
        int padding = width - sourceBytes;
        for (int i = 0; i < samples; i++) {
            int from = sourceOffset + i * sourceBytes;
            int to = targetOffset + i * width;
            for (int p = 0; p < padding; p++) {
                target[to + p] = 0;
            }
            for (int b = 0; b < sourceBytes; b++) {
                target[to + padding + b] = source[from + b];
            }
        }
        return samples * width;
    }
}
