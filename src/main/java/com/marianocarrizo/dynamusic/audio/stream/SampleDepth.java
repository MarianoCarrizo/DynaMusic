package com.marianocarrizo.dynamusic.audio.stream;

final class SampleDepth {

    private static final int DROPPED_BITS = 8;
    private static final int HALF_STEP = 1 << (DROPPED_BITS - 1);

    private SampleDepth() {
    }

    static int reduceToSixteen(byte[] in, int inLength, byte[] out) {
        SampleWidth from = SampleWidth.BITS_24;
        SampleWidth to = SampleWidth.BITS_16;
        int samples = inLength / from.bytes();
        for (int i = 0; i < samples; i++) {
            int value = from.read(in, i * from.bytes());
            to.write(out, i * to.bytes(), to.clamp((value + HALF_STEP) >> DROPPED_BITS));
        }
        return samples * to.bytes();
    }
}
