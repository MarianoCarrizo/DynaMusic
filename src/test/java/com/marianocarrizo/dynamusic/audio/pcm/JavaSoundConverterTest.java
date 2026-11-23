package com.marianocarrizo.dynamusic.audio.pcm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSoundConverterTest {

    private static final File FLAC_24 = Paths.get("src", "test", "resources", "audio", "tone-24bit.flac").toFile();
    private static final File FLAC_16 = Paths.get("src", "test", "resources", "audio", "tone-16bit.flac").toFile();

    @TempDir Path dir;

    private static int bitsOf(Path wav) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(wav.toFile())) {
            return stream.getFormat().getSampleSizeInBits();
        }
    }

    @Test
    void twentyFourBitFlacKeepsItsDepth() throws Exception {
        Path target = dir.resolve("out.wav");

        new JavaSoundConverter().convert(FLAC_24, target);

        assertEquals(24, bitsOf(target));
        assertTrue(AudioFileGain.measure(target.toFile()) <= 1.0);
    }

    @Test
    void sixteenBitFlacStaysSixteenBit() throws Exception {
        Path target = dir.resolve("out.wav");

        new JavaSoundConverter().convert(FLAC_16, target);

        assertEquals(16, bitsOf(target));
    }

    @Test
    void truncatedFlacFailsAndLeavesNoOutput() throws IOException {
        byte[] full = Files.readAllBytes(FLAC_24.toPath());
        Path truncated = Files.write(dir.resolve("cut.flac"), Arrays.copyOf(full, full.length / 2));
        Path target = dir.resolve("out.wav");

        assertThrows(IOException.class, () -> new JavaSoundConverter().convert(truncated.toFile(), target));
        assertFalse(Files.exists(target));
    }

    @Test
    void notAnAudioFileFails() throws IOException {
        Path garbage = Files.write(dir.resolve("noise.flac"), new byte[512]);
        Path target = dir.resolve("out.wav");

        assertThrows(IOException.class, () -> new JavaSoundConverter().convert(garbage.toFile(), target));
        assertFalse(Files.exists(target));
    }
}
