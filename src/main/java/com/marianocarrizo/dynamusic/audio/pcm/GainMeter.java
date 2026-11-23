package com.marianocarrizo.dynamusic.audio.pcm;

public final class GainMeter {

    static final double TARGET_RMS = 0.1;
    static final double MAX_GAIN = 1.0;
    private static final int BITS_PER_BYTE = 8;

    private final int bytesPerSample;
    private final int bits;
    private final boolean signed;
    private final boolean bigEndian;
    private final byte[] carry;
    private int carried;
    private double sumOfSquares;
    private long sampleCount;

    public GainMeter(int bitsPerSample, boolean signed, boolean bigEndian) {
        if (bitsPerSample % BITS_PER_BYTE != 0 || bitsPerSample < BITS_PER_BYTE || bitsPerSample > 32) {
            throw new IllegalArgumentException("unsupported sample size: " + bitsPerSample);
        }
        this.bits = bitsPerSample;
        this.bytesPerSample = bitsPerSample / BITS_PER_BYTE;
        this.signed = signed;
        this.bigEndian = bigEndian;
        this.carry = new byte[bytesPerSample];
    }

    public void accept(byte[] buffer, int length) {
        int offset = 0;
        while (offset < length) {
            carry[carried++] = buffer[offset++];
            if (carried == bytesPerSample) {
                addSample(decode());
                carried = 0;
            }
        }
    }

    public double gain() {
        if (sampleCount == 0) return MAX_GAIN;
        double rms = Math.sqrt(sumOfSquares / sampleCount);
        if (rms == 0) return MAX_GAIN;
        return Math.min(TARGET_RMS / rms, MAX_GAIN);
    }

    private double decode() {
        long value = 0;
        for (int i = 0; i < bytesPerSample; i++) {
            int index = bigEndian ? i : bytesPerSample - 1 - i;
            value = (value << BITS_PER_BYTE) | (carry[index] & 0xFF);
        }
        long signBit = 1L << (bits - 1);
        if (signed && (value & signBit) != 0) value -= 1L << bits;
        if (!signed) value -= signBit;
        return value / (double) signBit;
    }

    private void addSample(double normalized) {
        sumOfSquares += normalized * normalized;
        sampleCount++;
    }
}
