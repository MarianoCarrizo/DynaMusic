package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Mp4ProbeTest {

    @TempDir Path dir;

    private Path write(byte[] bytes) throws IOException {
        return Files.write(dir.resolve("a.m4a"), bytes);
    }

    @Test
    void alacSampleEntryIsDetected() throws IOException {
        assertTrue(Mp4Probe.isAlac(write(AudioFixtures.m4a("alac", false)).toFile()));
    }

    @Test
    void aacSampleEntryIsNotAlac() throws IOException {
        assertFalse(Mp4Probe.isAlac(write(AudioFixtures.m4a("mp4a", true)).toFile()));
    }

    @Test
    void truncatedAndGarbageFilesAreNotAlac() throws IOException {
        byte[] full = AudioFixtures.m4a("alac", true);

        assertFalse(Mp4Probe.isAlac(write(Arrays.copyOf(full, full.length - 20)).toFile()));
        assertFalse(Mp4Probe.isAlac(write(new byte[256]).toFile()));
        assertFalse(Mp4Probe.isAlac(dir.resolve("missing.m4a").toFile()));
    }

    @Test
    void atomSizesBelowTheHeaderStopTheWalk() {
        byte[] broken = {0, 0, 0, 2, 't', 'r', 'a', 'k', 0, 0, 0, 0};

        assertFalse(Mp4Probe.containsLosslessEntry(broken, 0, broken.length));
    }
}
