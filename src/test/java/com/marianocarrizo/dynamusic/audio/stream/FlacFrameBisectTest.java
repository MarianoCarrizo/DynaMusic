package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlacFrameBisectTest {

    private static final int SMALL_SPAN = 1024;
    private static final byte[] FALSE_SYNC = {(byte) 0xFF, (byte) 0xF8, 0x19, 0x18, 0x03};

    @TempDir Path dir;

    private static final long[] TARGETS = {0, 1, 1023, 1024, 5000, 8820, 17000, FlacFixtures.TOTAL_FRAMES - 1};

    private static void assertMatchesSeekTable(int bits, int span) throws Exception {
        try (RandomAccessFile withTable = new RandomAccessFile(FlacFixtures.file(bits, true), "r");
                RandomAccessFile without = new RandomAccessFile(FlacFixtures.file(bits, false), "r")) {
            FlacMetadata tableMetadata = FlacMetadata.read(withTable);
            FlacMetadata plainMetadata = FlacMetadata.read(without);
            FlacFrameBisect bisect = new FlacFrameBisect(without, plainMetadata.firstFrameOffset(),
                    plainMetadata.streamInfo().getMinBlockSize(), plainMetadata.streamInfo().getMaxBlockSize(), span);
            for (long target : TARGETS) {
                FlacSeekPoint expected = tableMetadata.seekIndex().locate(target);
                FlacSeekPoint found = bisect.locate(target);
                assertEquals(expected.sample(), found.sample(), "sample for target " + target);
                assertEquals(expected.offset() - tableMetadata.firstFrameOffset(),
                        found.offset() - plainMetadata.firstFrameOffset(), "offset for target " + target);
            }
        }
    }

    @Test
    void bisectionFindsTheSameFramesAsTheSeekTable16Bit() throws Exception {
        assertMatchesSeekTable(16, SMALL_SPAN);
    }

    @Test
    void bisectionFindsTheSameFramesAsTheSeekTable24Bit() throws Exception {
        assertMatchesSeekTable(24, SMALL_SPAN);
    }

    @Test
    void defaultSpanScansLinearlyOnSmallFiles() throws Exception {
        assertMatchesSeekTable(16, FlacFrameBisect.DEFAULT_SPAN_BYTES);
    }

    private File withFalseSyncAt(double fraction) throws Exception {
        byte[] data = Files.readAllBytes(FlacFixtures.file(16, false).toPath());
        int at = (int) (data.length * fraction);
        System.arraycopy(FALSE_SYNC, 0, data, at, FALSE_SYNC.length);
        data[at + FALSE_SYNC.length] = (byte) Crc8.compute(data, at, at + FALSE_SYNC.length);
        Path damaged = dir.resolve("false-sync-" + at + ".flac");
        Files.write(damaged, data);
        return damaged.toFile();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.2, 0.3, 0.4, 0.45, 0.5, 0.55, 0.6, 0.7, 0.75, 0.8, 0.9})
    void aFalseSyncInsideAFrameIsNotChosenAsASeekPoint(double fraction) throws Exception {
        try (RandomAccessFile clean = new RandomAccessFile(FlacFixtures.file(16, false), "r");
                RandomAccessFile damaged = new RandomAccessFile(withFalseSyncAt(fraction), "r")) {
            FlacMetadata metadata = FlacMetadata.read(clean);
            int block = metadata.streamInfo().getMinBlockSize();
            FlacFrameBisect expected = new FlacFrameBisect(clean, metadata.firstFrameOffset(), block, block, SMALL_SPAN);
            FlacFrameBisect actual = new FlacFrameBisect(damaged, metadata.firstFrameOffset(), block, block, SMALL_SPAN);
            for (long target : TARGETS) {
                assertEquals(expected.locate(target), actual.locate(target), "target " + target);
            }
        }
    }
}
