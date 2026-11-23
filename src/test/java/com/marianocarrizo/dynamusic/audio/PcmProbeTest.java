package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PcmProbeTest {

    private static PcmProbe.Info wav(int bits, int rate, boolean floating) {
        return PcmProbe.parse(AudioFormat.WAV, AudioFixtures.wav(bits, rate, 2, floating)).orElseThrow();
    }

    @Test
    void readsWavDepthAndRate() {
        PcmProbe.Info info = wav(24, 96000, false);

        assertEquals(24, info.bitsPerSample());
        assertEquals(96000, info.sampleRate());
        assertFalse(info.floatingPoint());
    }

    @Test
    void sixteenBitCdAudioStaysNative() {
        PcmProbe.Info info = wav(16, 44100, false);

        assertFalse(info.exceedsNativeDepth());
        assertFalse(info.exceedsNativeRate());
    }

    @Test
    void thirtyTwoBitIntegerExceedsTheNativeDepth() {
        assertTrue(wav(32, 44100, false).exceedsNativeDepth());
    }

    @Test
    void thirtyTwoBitFloatIsNotTooDeep() {
        PcmProbe.Info info = wav(32, 44100, true);

        assertTrue(info.floatingPoint());
        assertFalse(info.exceedsNativeDepth());
    }

    @Test
    void ratesAbove96kExceedTheNativeRate() {
        assertTrue(wav(24, 192000, false).exceedsNativeRate());
        assertFalse(wav(24, 96000, false).exceedsNativeRate());
    }

    @Test
    void readsAiffDepthAndRate() {
        PcmProbe.Info info = PcmProbe.parse(AudioFormat.AIFF, AudioFixtures.aiff(24, 48000)).orElseThrow();

        assertEquals(24, info.bitsPerSample());
        assertEquals(48000, info.sampleRate());
    }

    @Test
    void readsFlacStreamInfo() {
        PcmProbe.Info info = PcmProbe.parse(AudioFormat.FLAC, AudioFixtures.flacHeader(192000, 24)).orElseThrow();

        assertEquals(192000, info.sampleRate());
        assertEquals(24, info.bitsPerSample());
    }

    @Test
    void garbageAndTruncatedHeadersGiveNothing() {
        assertTrue(PcmProbe.parse(AudioFormat.WAV, new byte[100]).isEmpty());
        assertTrue(PcmProbe.parse(AudioFormat.WAV, Arrays.copyOf(AudioFixtures.wav(16, 44100, 2, false), 20)).isEmpty());
        assertTrue(PcmProbe.parse(AudioFormat.AIFF, new byte[5]).isEmpty());
        assertTrue(PcmProbe.parse(AudioFormat.FLAC, new byte[8]).isEmpty());
        assertEquals(Optional.empty(), PcmProbe.parse(AudioFormat.MP3, new byte[100]));
    }
}
