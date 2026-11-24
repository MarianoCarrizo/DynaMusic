package com.marianocarrizo.dynamusic.audio.wasapi;

public final class FloatPcm {

    public static final int OUTPUT_CHANNELS = 2;
    public static final int FRAME_BYTES = OUTPUT_CHANNELS * Float.BYTES;

    private static final int BITS_PER_BYTE = 8;
    private static final int BYTE_MASK = 0xFF;
    private static final int MONO = 1;

    private FloatPcm() {
    }

    public static boolean isSourceSupported(int sourceBits, int channels) {
        return SampleContainer.isSourceSupported(sourceBits) && channels >= MONO && channels <= OUTPUT_CHANNELS;
    }

    public static int convert(byte[] source, int sourceOffset, int sourceBits, int channels, int frames,
            byte[] target) {
        int sourceBytes = sourceBits / BITS_PER_BYTE;
        float scale = 1 << (sourceBits - 1);
        int read = sourceOffset;
        int written = 0;
        for (int frame = 0; frame < frames; frame++) {
            float left = sample(source, read, sourceBytes) / scale;
            float right = channels == MONO ? left : sample(source, read + sourceBytes, sourceBytes) / scale;
            write(target, written, left);
            write(target, written + Float.BYTES, right);
            read += channels * sourceBytes;
            written += FRAME_BYTES;
        }
        return written;
    }

    private static int sample(byte[] source, int offset, int width) {
        int value = source[offset + width - 1];
        for (int b = width - 2; b >= 0; b--) {
            value = (value << BITS_PER_BYTE) | (source[offset + b] & BYTE_MASK);
        }
        return value;
    }

    private static void write(byte[] target, int offset, float value) {
        int bits = Float.floatToRawIntBits(value);
        for (int b = 0; b < Float.BYTES; b++) {
            target[offset + b] = (byte) (bits >> (BITS_PER_BYTE * b));
        }
    }
}
