package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.RandomAccessFile;
import org.junit.jupiter.api.Test;

class FlacFrameBisectTest {

    private static final int SMALL_SPAN = 1024;
    private static final long[] TARGETS = {0, 1, 1023, 1024, 5000, 8820, 17000, FlacFixtures.TOTAL_FRAMES - 1};

    private static void assertMatchesSeekTable(int bits, int span) throws Exception {
        try (RandomAccessFile withTable = new RandomAccessFile(FlacFixtures.file(bits, true), "r");
                RandomAccessFile without = new RandomAccessFile(FlacFixtures.file(bits, false), "r")) {
            FlacMetadata tableMetadata = FlacMetadata.read(withTable);
            FlacMetadata plainMetadata = FlacMetadata.read(without);
            FlacFrameBisect bisect = new FlacFrameBisect(without, plainMetadata.firstFrameOffset(),
                    plainMetadata.streamInfo().getMinBlockSize(), span);
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
}
