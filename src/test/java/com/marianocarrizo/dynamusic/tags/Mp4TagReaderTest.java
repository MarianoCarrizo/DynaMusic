package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Mp4TagReaderTest {

    private static final byte[] ITEMS_TITLE_ARTIST_ALBUM = TagFixtures.concat(
            TagFixtures.textItem("nam", "Song"),
            TagFixtures.textItem("ART", "Band"),
            TagFixtures.textItem("alb", "Rec"),
            TagFixtures.textItem("day", "2015-01-01"),
            TagFixtures.trackItem(3, 12));

    private static TrackTags read(byte[] bytes) {
        return TagFormat.MP4.readTags(ByteSource.ofBytes(bytes));
    }

    @Test
    void readsFieldsAndTrackNumber() {
        TrackTags tags = read(TagFixtures.mp4File(TagFixtures.mp4Meta(ITEMS_TITLE_ARTIST_ALBUM), false));

        assertEquals("Song", tags.title());
        assertEquals("Band", tags.artist());
        assertEquals("Rec", tags.album());
        assertEquals("2015", tags.year());
        assertEquals(3, tags.trackNumber());
    }

    @Test
    void findsMoovAfterTheMediaData() {
        TrackTags tags = read(TagFixtures.mp4File(TagFixtures.mp4Meta(ITEMS_TITLE_ARTIST_ALBUM), true));

        assertEquals("Song", tags.title());
    }

    @Test
    void readsTheCover() {
        byte[] file = TagFixtures.mp4File(TagFixtures.mp4Meta(TagFixtures.textItem("nam", "Song"),
                TagFixtures.coverItem(13, TagFixtures.JPEG)), false);

        assertTrue(read(file).hasCover());
        assertArrayEquals(TagFixtures.JPEG, TagFormat.MP4.readPicture(ByteSource.ofBytes(file)).orElseThrow());
    }

    @Test
    void atomSmallerThanItsHeaderStopsWithoutLooping() {
        byte[] broken = TagFixtures.concat(TagFixtures.u32be(4), "free".getBytes(StandardCharsets.US_ASCII),
                new byte[32]);

        assertEquals(TrackTags.EMPTY, read(broken));
    }

    @Test
    void atomLargerThanTheFileStops() {
        byte[] broken = TagFixtures.concat(TagFixtures.u32be(0x7FFFFFFFL), "moov".getBytes(StandardCharsets.US_ASCII),
                new byte[32]);

        assertEquals(TrackTags.EMPTY, read(broken));
    }

    @Test
    void sizeZeroMeansUntilTheEndOfTheFile() {
        byte[] moov = TagFixtures.mp4Meta(TagFixtures.textItem("nam", "Song"));
        byte[] zeroSized = Arrays.copyOf(moov, moov.length);
        System.arraycopy(TagFixtures.u32be(0), 0, zeroSized, 0, 4);

        assertEquals("Song", read(zeroSized).title());
    }

    @Test
    void extendedSizeAtomsAreWalked() {
        byte[] mdat = TagFixtures.concat(TagFixtures.u32be(1), "mdat".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0, 0, 0, 0, 0, 0, 0, 24}, new byte[8]);
        byte[] file = TagFixtures.concat(mdat, TagFixtures.mp4Meta(TagFixtures.textItem("nam", "Song")));

        assertEquals("Song", read(file).title());
    }

    @Test
    void truncatedFileDoesNotFail() {
        byte[] file = TagFixtures.mp4File(TagFixtures.mp4Meta(ITEMS_TITLE_ARTIST_ALBUM), false);

        assertEquals(TrackTags.EMPTY, read(Arrays.copyOf(file, 40)));
    }
}
