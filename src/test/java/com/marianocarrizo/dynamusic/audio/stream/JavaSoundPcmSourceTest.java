package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaSoundPcmSourceTest {

    private static final int FRAMES = 6000;
    private static final int CHANNELS = 2;
    private static final int RATE = 44100;
    private static final int[] SEEK_TARGETS = {0, 1, 2999, 5999, FRAMES};

    @TempDir Path dir;

    private static byte[] expectedPcm(int bits) {
        SampleWidth width = SampleWidth.ofBits(bits);
        int[] samples = new int[FRAMES * CHANNELS];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = width.clamp((long) (i * 977 % 60000 - 30000) * (bits == 24 ? 251 : 1));
        }
        return PcmBytes.pack(width, samples);
    }

    private File write(String name, AudioFileFormat.Type type, int bits) throws IOException {
        byte[] pcm = expectedPcm(bits);
        AudioFormat littleEndian = PcmFormats.signedLittleEndian(bits, RATE, CHANNELS);
        File target = dir.resolve(name).toFile();
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), littleEndian, FRAMES)) {
            AudioSystem.write(stream, type, target);
        }
        return target;
    }

    private static byte[] readAll(File file) throws IOException {
        try (JavaSoundPcmSource source = JavaSoundPcmSource.open(file)) {
            return FlacFixtures.readAll(source);
        }
    }

    @ParameterizedTest
    @CsvSource({"16,WAVE", "24,WAVE", "16,AIFF", "24,AIFF"})
    void playsEveryFrameInLittleEndianOrder(int bits, String type) throws Exception {
        File file = write("tone." + type, fileType(type), bits);

        assertArrayEquals(expectedPcm(bits), readAll(file));
    }

    @ParameterizedTest
    @CsvSource({"16,WAVE", "24,WAVE", "16,AIFF", "24,AIFF"})
    void reportsFormatAndFrameCount(int bits, String type) throws Exception {
        File file = write("tone." + type, fileType(type), bits);
        try (JavaSoundPcmSource source = JavaSoundPcmSource.open(file)) {
            assertEquals(FRAMES, source.totalFrames());
            assertEquals(bits, source.format().getSampleSizeInBits());
            assertEquals(CHANNELS, source.format().getChannels());
            assertEquals((float) RATE, source.format().getSampleRate());
            assertEquals(false, source.format().isBigEndian());
        }
    }

    @ParameterizedTest
    @CsvSource({"16,WAVE", "24,WAVE", "16,AIFF", "24,AIFF"})
    void seekLandsOnTheRequestedFrame(int bits, String type) throws Exception {
        File file = write("tone." + type, fileType(type), bits);
        byte[] expected = expectedPcm(bits);
        int frameBytes = CHANNELS * bits / 8;
        try (JavaSoundPcmSource source = JavaSoundPcmSource.open(file)) {
            for (int target : SEEK_TARGETS) {
                source.seekToFrame(target);

                assertArrayEquals(Arrays.copyOfRange(expected, target * frameBytes, expected.length),
                        FlacFixtures.readAll(source), "tail after seek to " + target);
            }
        }
    }

    @Test
    void unsupportedSampleSizeIsRejected() throws Exception {
        AudioFormat eightBit = new AudioFormat(RATE, 8, CHANNELS, false, false);
        File file = dir.resolve("eight.wav").toFile();
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(new byte[400]), eightBit, 200)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }

        assertThrows(IOException.class, () -> JavaSoundPcmSource.open(file));
    }

    @Test
    void garbageFileIsRejected() throws Exception {
        Path bogus = dir.resolve("bogus.wav");
        Files.write(bogus, new byte[128]);

        assertThrows(IOException.class, () -> JavaSoundPcmSource.open(bogus.toFile()));
    }

    @Test
    void readAfterCloseReportsEndOfStream() throws Exception {
        JavaSoundPcmSource source = JavaSoundPcmSource.open(write("tone.wav", AudioFileFormat.Type.WAVE, 16));

        source.close();
        source.close();

        assertEquals(PcmSource.END_OF_STREAM, source.read(new byte[64]));
    }

    private static AudioFileFormat.Type fileType(String name) {
        return "AIFF".equals(name) ? AudioFileFormat.Type.AIFF : AudioFileFormat.Type.WAVE;
    }
}
