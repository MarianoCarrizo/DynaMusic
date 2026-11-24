package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlacPcmSourceTest {

    private static final long[] TARGETS = {0, 1, 1023, 1024, 1025, 7777, 8820, 16383, 17639};
    private static final int CHECK_FRAMES = 3000;

    @TempDir Path dir;

    private static byte[] linear(File file) throws IOException {
        try (FlacPcmSource source = FlacPcmSource.open(file)) {
            return FlacFixtures.readAll(source);
        }
    }

    @ParameterizedTest
    @CsvSource({"16,true", "16,false", "24,true", "24,false"})
    void reportsFormatAndTotalFromStreamInfo(int bits, boolean seekTable) throws Exception {
        try (FlacPcmSource source = FlacPcmSource.open(FlacFixtures.file(bits, seekTable))) {
            AudioFormat format = source.format();

            assertEquals(bits, format.getSampleSizeInBits());
            assertEquals(2, format.getChannels());
            assertEquals(44100f, format.getSampleRate());
            assertEquals(FlacFixtures.TOTAL_FRAMES, source.totalFrames());
        }
    }

    @ParameterizedTest
    @CsvSource({"16,true", "16,false", "24,true", "24,false"})
    void linearDecodeProducesEveryFrameOnce(int bits, boolean seekTable) throws Exception {
        byte[] pcm = linear(FlacFixtures.file(bits, seekTable));

        assertEquals((long) FlacFixtures.TOTAL_FRAMES * 2 * bits / 8, pcm.length);
        assertNotEquals(0, Arrays.mismatch(pcm, new byte[pcm.length]));
    }

    @ParameterizedTest
    @CsvSource({"16,true", "16,false", "24,true", "24,false"})
    void samplesAfterSeekEqualLinearDecode(int bits, boolean seekTable) throws Exception {
        File file = FlacFixtures.file(bits, seekTable);
        byte[] expected = linear(file);
        int frameBytes = 2 * bits / 8;
        try (FlacPcmSource source = FlacPcmSource.open(file)) {
            for (long target : TARGETS) {
                source.seekToFrame(target);
                byte[] actual = FlacFixtures.readAll(source);
                byte[] tail = Arrays.copyOfRange(expected, (int) (target * frameBytes), expected.length);

                assertArrayEquals(tail, actual, "tail after seek to " + target);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"16,true", "16,false", "24,true", "24,false"})
    void seekingBackwardAfterPartialReadRestartsCleanly(int bits, boolean seekTable) throws Exception {
        File file = FlacFixtures.file(bits, seekTable);
        byte[] expected = linear(file);
        int frameBytes = 2 * bits / 8;
        try (FlacPcmSource source = FlacPcmSource.open(file)) {
            source.seekToFrame(9000);
            source.read(new byte[1000 * frameBytes]);
            source.seekToFrame(100);
            byte[] chunk = new byte[CHECK_FRAMES * frameBytes];
            int read = source.read(chunk);

            assertArrayEquals(Arrays.copyOfRange(expected, 100 * frameBytes, 100 * frameBytes + read),
                    Arrays.copyOf(chunk, read));
        }
    }

    @ParameterizedTest
    @CsvSource({"16,true", "24,false"})
    void seekingToTheEndYieldsEndOfStream(int bits, boolean seekTable) throws Exception {
        try (FlacPcmSource source = FlacPcmSource.open(FlacFixtures.file(bits, seekTable))) {
            source.seekToFrame(FlacFixtures.TOTAL_FRAMES);

            assertEquals(PcmSource.END_OF_STREAM, source.read(new byte[4096]));
        }
    }

    @ParameterizedTest
    @CsvSource({"16,true", "24,false"})
    void readsAreFrameAligned(int bits, boolean seekTable) throws Exception {
        try (FlacPcmSource source = FlacPcmSource.open(FlacFixtures.file(bits, seekTable))) {
            int count = source.read(new byte[1001]);

            assertEquals(0, count % (2 * bits / 8));
        }
    }

    @ParameterizedTest
    @CsvSource({"16,true", "24,false"})
    void closeIsIdempotent(int bits, boolean seekTable) throws Exception {
        FlacPcmSource source = FlacPcmSource.open(FlacFixtures.file(bits, seekTable));

        source.close();
        source.close();
    }

    @Test
    void nonFlacFileIsRejected() throws Exception {
        Path bogus = dir.resolve("bogus.flac");
        Files.write(bogus, new byte[64]);

        assertThrows(IOException.class, () -> FlacPcmSource.open(bogus.toFile()));
    }

    private File withSeekPointMovedInsideAFrame(int pointIndex, int shift) throws IOException {
        byte[] data = Files.readAllBytes(FlacFixtures.file(16, true).toPath());
        int position = 4;
        while ((data[position] & 0x7F) != 3) {
            position += 4 + ((data[position + 1] & 0xFF) << 16 | (data[position + 2] & 0xFF) << 8 | (data[position + 3] & 0xFF));
        }
        int offsetField = position + 4 + pointIndex * 18 + 8 + 7;
        data[offsetField] = (byte) (data[offsetField] + shift);
        Path damaged = dir.resolve("moved-seek-point.flac");
        Files.write(damaged, data);
        return damaged.toFile();
    }

    @ParameterizedTest
    @CsvSource({"3,7", "5,40", "8,200"})
    void aSeekPointLandingInsideAFrameResyncsInsteadOfFailing(int pointIndex, int shift) throws Exception {
        byte[] expected = linear(FlacFixtures.file(16, true));
        int frameBytes = 4;
        try (FlacPcmSource source = FlacPcmSource.open(withSeekPointMovedInsideAFrame(pointIndex, shift))) {
            long target = (long) pointIndex * FlacFixtures.BLOCK_SIZE;
            source.seekToFrame(target);
            byte[] actual = FlacFixtures.readAll(source);
            byte[] tail = Arrays.copyOfRange(expected, (int) (target * frameBytes), expected.length);

            assertArrayEquals(tail, actual, "tail after seek to " + target);
        }
    }
}
