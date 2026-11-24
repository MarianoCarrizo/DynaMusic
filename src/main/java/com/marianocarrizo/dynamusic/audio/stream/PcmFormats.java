package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

public final class PcmFormats {

    private static final int BITS_PER_BYTE = 8;

    private PcmFormats() {
    }

    public static AudioFormat signedLittleEndian(int bits, float sampleRate, int channels) {
        int frameSize = bits / BITS_PER_BYTE * channels;
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, bits, channels, frameSize,
                sampleRate, false);
    }

    public static AudioFormat withBits(AudioFormat format, int bits) {
        return signedLittleEndian(bits, format.getSampleRate(), format.getChannels());
    }
}
