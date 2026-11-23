package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.tags.TagFormat;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AudioFormatTest {

    @Test
    void lookupIgnoresCaseAndLeadingDot() {
        assertEquals(Optional.of(AudioFormat.FLAC), AudioFormat.fromExtension("FLAC"));
        assertEquals(Optional.of(AudioFormat.FLAC), AudioFormat.fromExtension(".flac"));
        assertEquals(Optional.of(AudioFormat.FLAC), AudioFormat.fromFileName("song.FLAC"));
        assertEquals(PlaybackRoute.JAVA_SOUND, AudioFormat.FLAC.route());
    }

    @Test
    void unknownOrMissingExtensionHasNoFormat() {
        assertTrue(AudioFormat.fromFileName("notes.txt").isEmpty());
        assertTrue(AudioFormat.fromFileName("noextension").isEmpty());
        assertTrue(AudioFormat.fromFileName("trailingdot.").isEmpty());
        assertTrue(AudioFormat.fromExtension(null).isEmpty());
        assertTrue(AudioFormat.fromExtension("").isEmpty());
        assertFalse(AudioFormat.isSupported(new File("notes.txt")));
        assertFalse(AudioFormat.isSupported(null));
    }

    @Test
    void aliasesResolveToTheSameFormat() {
        assertEquals(Optional.of(AudioFormat.AIFF), AudioFormat.fromExtension("aif"));
        assertEquals(Optional.of(AudioFormat.AIFF), AudioFormat.fromExtension("aiff"));
    }

    @Test
    void extensionsAreUniqueLowercaseAndWithoutDots() {
        List<String> all = AudioFormat.allExtensions();
        assertEquals(all.size(), new HashSet<>(all).size());
        for (String extension : all) {
            assertEquals(extension.toLowerCase(), extension);
            assertFalse(extension.startsWith("."));
        }
    }

    @Test
    void chooserPatternsAndDisplayListDeriveFromExtensions() {
        assertEquals(AudioFormat.allExtensions().size(), AudioFormat.chooserPatterns().size());
        assertTrue(AudioFormat.chooserPatterns().contains("*.flac"));
        assertTrue(AudioFormat.displayList().contains("flac"));
        assertTrue(AudioFormat.displayList().contains("opus"));
    }

    @Test
    void matchesOnlyItsOwnFiles() {
        assertTrue(AudioFormat.MP3.matches(new File("a.MP3")));
        assertFalse(AudioFormat.MP3.matches(new File("a.flac")));
        assertFalse(AudioFormat.MP3.matches(null));
    }

    @Test
    void routesAndTagFormatsMatchTheDesign() {
        assertEquals(PlaybackRoute.NATIVE, AudioFormat.MP3.route());
        assertEquals(PlaybackRoute.NATIVE, AudioFormat.M4A.route());
        assertEquals(PlaybackRoute.FFMPEG, AudioFormat.AAC.route());
        assertEquals(PlaybackRoute.FFMPEG, AudioFormat.OGG.route());
        assertEquals(TagFormat.ID3V2, AudioFormat.MP3.tagFormat());
        assertEquals(TagFormat.OGG, AudioFormat.OPUS.tagFormat());
        assertEquals(TagFormat.NONE, AudioFormat.WAV.tagFormat());
        assertEquals(PlaybackRoute.FFMPEG, AudioFormat.WMA.route());
    }
}
