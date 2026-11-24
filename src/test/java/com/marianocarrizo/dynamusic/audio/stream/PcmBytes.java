package com.marianocarrizo.dynamusic.audio.stream;

final class PcmBytes {

    private PcmBytes() {
    }

    static byte[] pack(SampleWidth width, int... samples) {
        byte[] data = new byte[samples.length * width.bytes()];
        for (int i = 0; i < samples.length; i++) {
            width.write(data, i * width.bytes(), samples[i]);
        }
        return data;
    }

    static int[] unpack(SampleWidth width, byte[] data, int length) {
        int[] samples = new int[length / width.bytes()];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = width.read(data, i * width.bytes());
        }
        return samples;
    }

    static int[] unpack(SampleWidth width, byte[] data) {
        return unpack(width, data, data.length);
    }
}
