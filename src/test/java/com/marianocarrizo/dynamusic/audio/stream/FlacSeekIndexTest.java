package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.RandomAccessFile;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlacSeekIndexTest {

    private static final long FIRST_FRAME = 100;
    private static final long PLACEHOLDER = -1L;

    private static FlacSeekIndex index() {
        return new FlacSeekIndex(FIRST_FRAME, List.of(new FlacSeekPoint(0, 0), new FlacSeekPoint(1000, 500),
                new FlacSeekPoint(2000, 900), new FlacSeekPoint(PLACEHOLDER, 0)));
    }

    @Test
    void offsetsBecomeAbsolute() {
        assertEquals(new FlacSeekPoint(1000, FIRST_FRAME + 500), index().locate(1000));
    }

    @Test
    void locatesLastPointAtOrBeforeTarget() {
        assertEquals(new FlacSeekPoint(1000, FIRST_FRAME + 500), index().locate(1999));
        assertEquals(new FlacSeekPoint(2000, FIRST_FRAME + 900), index().locate(50_000));
    }

    @Test
    void placeholderPointsAreIgnored() {
        assertEquals(new FlacSeekPoint(2000, FIRST_FRAME + 900), index().locate(Long.MAX_VALUE));
    }

    @Test
    void emptyIndexFallsBackToFirstFrame() {
        FlacSeekIndex empty = new FlacSeekIndex(FIRST_FRAME, List.of());

        assertFalse(empty.hasPoints());
        assertEquals(new FlacSeekPoint(0, FIRST_FRAME), empty.locate(12345));
    }

    @Test
    void indexWithPointsReportsThem() {
        assertTrue(index().hasPoints());
    }

    @Test
    void realSeekTableIsReadFromTheFixture() throws Exception {
        long target = 5L * FlacFixtures.BLOCK_SIZE + 3;
        try (RandomAccessFile file = new RandomAccessFile(FlacFixtures.file(16, true), "r")) {
            FlacMetadata metadata = FlacMetadata.read(file);

            assertTrue(metadata.seekIndex().hasPoints());
            assertEquals(5L * FlacFixtures.BLOCK_SIZE, metadata.seekIndex().locate(target).sample());
        }
    }

    @Test
    void fixtureWithoutSeekTableHasNoPoints() throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(FlacFixtures.file(16, false), "r")) {
            assertFalse(FlacMetadata.read(file).seekIndex().hasPoints());
        }
    }
}
