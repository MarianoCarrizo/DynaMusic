package com.marianocarrizo.dynamusic.audio.wasapi;

final class PcmFrames {

    private static final int BYTE_MASK = 0xFF;
    private static final int BITS_PER_BYTE = 8;

    private PcmFrames() {
    }

    static byte[] pack(int bytesPerSample, int... samples) {
        byte[] data = new byte[samples.length * bytesPerSample];
        for (int i = 0; i < samples.length; i++) {
            for (int b = 0; b < bytesPerSample; b++) {
                data[i * bytesPerSample + b] = (byte) (samples[i] >> (BITS_PER_BYTE * b));
            }
        }
        return data;
    }

    static int[] unpack(int bytesPerSample, byte[] data) {
        int[] samples = new int[data.length / bytesPerSample];
        for (int i = 0; i < samples.length; i++) {
            int value = data[i * bytesPerSample + bytesPerSample - 1];
            for (int b = bytesPerSample - 2; b >= 0; b--) {
                value = (value << BITS_PER_BYTE) | (data[i * bytesPerSample + b] & BYTE_MASK);
            }
            samples[i] = value;
        }
        return samples;
    }

    static int[] ramp(int count, int first) {
        int[] samples = new int[count];
        for (int i = 0; i < count; i++) {
            samples[i] = first + i;
        }
        return samples;
    }
}
