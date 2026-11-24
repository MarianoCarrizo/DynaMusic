package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.PcmProbe;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EngineChoiceTest {

    private static final PcmProbe.Info CD_QUALITY = new PcmProbe.Info(16, 44100, false);

    private static EngineChoice choose(boolean enabled, PlaybackUse use, String name, Optional<PcmProbe.Info> info) {
        return EngineChoice.of(enabled, use, new File(name), () -> info);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.flac", "a.wav", "a.aiff", "a.aif"})
    void streamableFormatsOnListenPathsStreamWhenEnabled(String name) {
        assertEquals(EngineChoice.STREAMING, choose(true, PlaybackUse.LISTEN, name, Optional.of(CD_QUALITY)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.flac", "a.wav", "a.aiff"})
    void settingOffAlwaysUsesJavaFx(String name) {
        assertEquals(EngineChoice.JAVAFX, choose(false, PlaybackUse.LISTEN, name, Optional.of(CD_QUALITY)));
    }

    @Test
    void moodsAlwaysUseJavaFx() {
        assertEquals(EngineChoice.JAVAFX, choose(true, PlaybackUse.MOOD, "a.flac", Optional.of(CD_QUALITY)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.mp3", "a.ogg", "a.opus", "a.m4a", "a.aac", "a.wma", "a.ape", "a.txt"})
    void otherFormatsUseJavaFx(String name) {
        assertEquals(EngineChoice.JAVAFX, choose(true, PlaybackUse.LISTEN, name, Optional.of(CD_QUALITY)));
    }

    @Test
    void twentyFourBitStreams() {
        assertEquals(EngineChoice.STREAMING,
                choose(true, PlaybackUse.LISTEN, "a.flac", Optional.of(new PcmProbe.Info(24, 96000, false))));
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 12, 20, 32})
    void unsupportedSampleSizesUseJavaFx(int bits) {
        assertEquals(EngineChoice.JAVAFX,
                choose(true, PlaybackUse.LISTEN, "a.wav", Optional.of(new PcmProbe.Info(bits, 44100, false))));
    }

    @Test
    void floatingPointUsesJavaFx() {
        assertEquals(EngineChoice.JAVAFX,
                choose(true, PlaybackUse.LISTEN, "a.wav", Optional.of(new PcmProbe.Info(16, 44100, true))));
    }

    @Test
    void unreadableHeaderUsesJavaFx() {
        assertEquals(EngineChoice.JAVAFX, choose(true, PlaybackUse.LISTEN, "a.flac", Optional.empty()));
    }

    @Test
    void probeIsOnlyReadWhenStreamingIsPossible() {
        AtomicInteger reads = new AtomicInteger();

        EngineChoice.of(false, PlaybackUse.LISTEN, new File("a.flac"), () -> countRead(reads));
        EngineChoice.of(true, PlaybackUse.MOOD, new File("a.flac"), () -> countRead(reads));
        EngineChoice.of(true, PlaybackUse.LISTEN, new File("a.mp3"), () -> countRead(reads));

        assertEquals(0, reads.get());
    }

    @Test
    void couldStreamNeedsTheSettingAndAStreamableFormat() {
        assertTrue(EngineChoice.couldStream(true, new File("a.flac")));
        assertFalse(EngineChoice.couldStream(false, new File("a.flac")));
        assertFalse(EngineChoice.couldStream(true, new File("a.mp3")));
    }

    private static Optional<PcmProbe.Info> countRead(AtomicInteger reads) {
        reads.incrementAndGet();
        return Optional.of(CD_QUALITY);
    }
}
