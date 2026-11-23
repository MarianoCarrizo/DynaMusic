package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

class PlaybackSupportTest {

    @Test
    void nativeAndJavaSoundFormatsPlayWithoutFfmpeg() {
        assertTrue(PlaybackSupport.isPlayable(new File("a.mp3"), false));
        assertTrue(PlaybackSupport.isPlayable(new File("a.wav"), false));
        assertTrue(PlaybackSupport.isPlayable(new File("a.flac"), false));
    }

    @Test
    void ffmpegFormatsNeedFfmpeg() {
        assertFalse(PlaybackSupport.isPlayable(new File("a.ogg"), false));
        assertFalse(PlaybackSupport.isPlayable(new File("a.opus"), false));
        assertTrue(PlaybackSupport.isPlayable(new File("a.ogg"), true));
    }

    @Test
    void unknownFilesAreNeverPlayable() {
        assertFalse(PlaybackSupport.isPlayable(new File("notes.txt"), true));
        assertFalse(PlaybackSupport.isPlayable(null, true));
    }
}
