package com.marianocarrizo.dynamusic.audio.stream;

enum SampleWidth {
    BITS_16(16),
    BITS_24(24);

    private static final int BITS_PER_BYTE = 8;
    private static final int BYTE_MASK = 0xFF;

    private final int bits;
    private final int bytes;

    SampleWidth(int bits) {
        this.bits = bits;
        this.bytes = bits / BITS_PER_BYTE;
    }

    static boolean isSupported(int bits) {
        for (SampleWidth width : values()) {
            if (width.bits == bits) return true;
        }
        return false;
    }

    static SampleWidth ofBits(int bits) {
        for (SampleWidth width : values()) {
            if (width.bits == bits) return width;
        }
        throw new IllegalArgumentException("Unsupported sample size: " + bits);
    }

    int bytes() {
        return bytes;
    }

    int max() {
        return (1 << (bits - 1)) - 1;
    }

    int min() {
        return -(1 << (bits - 1));
    }

    int clamp(long value) {
        return (int) Math.max(min(), Math.min(max(), value));
    }

    int read(byte[] data, int offset) {
        int value = data[offset + bytes - 1];
        for (int i = bytes - 2; i >= 0; i--) {
            value = (value << BITS_PER_BYTE) | (data[offset + i] & BYTE_MASK);
        }
        return value;
    }

    void write(byte[] data, int offset, int value) {
        for (int i = 0; i < bytes; i++) {
            data[offset + i] = (byte) (value >> (BITS_PER_BYTE * i));
        }
    }
}
