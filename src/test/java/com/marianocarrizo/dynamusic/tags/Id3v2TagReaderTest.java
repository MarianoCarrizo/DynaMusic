package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Id3v2TagReaderTest {

    private static TrackTags read(byte[] bytes) {
        return TagFormat.ID3V2.readTags(ByteSource.ofBytes(bytes));
    }

    @Test
    void readsVersion23Frames() {
        byte[] tag = TagFixtures.id3Tag(3, 0,
                TagFixtures.id3Text(3, "TIT2", 0, "Song"),
                TagFixtures.id3Text(3, "TPE1", 0, "Band"),
                TagFixtures.id3Text(3, "TALB", 0, "Rec"),
                TagFixtures.id3Text(3, "TRCK", 0, "7/10"),
                TagFixtures.id3Text(3, "TYER", 0, "2001"));

        TrackTags tags = read(tag);

        assertEquals("Song", tags.title());
        assertEquals("Band", tags.artist());
        assertEquals("Rec", tags.album());
        assertEquals(7, tags.trackNumber());
        assertEquals("2001", tags.year());
    }

    @Test
    void readsVersion24FramesWithSynchsafeSizes() {
        byte[] tag = TagFixtures.id3Tag(4, 0,
                TagFixtures.id3Text(4, "TIT2", 3, "Cancion"),
                TagFixtures.id3Text(4, "TDRC", 3, "2020-05-01"));

        TrackTags tags = read(tag);

        assertEquals("Cancion", tags.title());
        assertEquals("2020", tags.year());
    }

    @Test
    void decodesUtf16WithByteOrderMark() {
        byte[] tag = TagFixtures.id3Tag(3, 0, TagFixtures.id3Text(3, "TIT2", 1, "Canción"));

        assertEquals("Canción", read(tag).title());
    }

    @Test
    void decodesUtf16BigEndianWithoutByteOrderMark() {
        byte[] tag = TagFixtures.id3Tag(4, 0, TagFixtures.id3Text(4, "TIT2", 2, "Tema"));

        assertEquals("Tema", read(tag).title());
    }

    @Test
    void multipleArtistValuesAreJoined() {
        byte[] tag = TagFixtures.id3Tag(4, 0, TagFixtures.id3Text(4, "TPE1", 3, "A\0B"));

        assertEquals("A, B", read(tag).artist());
    }

    @Test
    void corruptFrameSizeStopsAndKeepsEarlierFrames() {
        byte[] good = TagFixtures.id3Text(3, "TIT2", 0, "Song");
        byte[] bad = TagFixtures.concat("TPE1".getBytes(StandardCharsets.US_ASCII), TagFixtures.u32be(0x7FFFFFFFL),
                new byte[] {0, 0}, new byte[10]);

        TrackTags tags = read(TagFixtures.id3Tag(3, 0, good, bad));

        assertEquals("Song", tags.title());
        assertEquals("", tags.artist());
    }

    @Test
    void fileWithoutTagIsEmpty() {
        byte[] audio = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        assertEquals(TrackTags.EMPTY, read(audio));
    }

    @Test
    void paddingEndsParsingCleanly() {
        byte[] tag = TagFixtures.concat(TagFixtures.id3Tag(3, 0, TagFixtures.id3Text(3, "TIT2", 0, "Song")),
                new byte[0]);
        byte[] padded = TagFixtures.id3Tag(3, 0, TagFixtures.id3Text(3, "TIT2", 0, "Song"), new byte[64]);

        assertEquals("Song", read(tag).title());
        assertEquals("Song", read(padded).title());
    }

    @Test
    void unsynchronisedTagsAreDecoded() {
        byte[] frame = TagFixtures.id3Text(3, "TIT2", 0, "Song");
        byte[] tag = TagFixtures.id3Tag(3, 0x80, frame);

        assertEquals("Song", read(tag).title());
    }

    @Test
    void truncatedTagReturnsWhatWasRead() {
        byte[] tag = TagFixtures.id3Tag(3, 0, TagFixtures.id3Text(3, "TIT2", 0, "Song"),
                TagFixtures.id3Text(3, "TPE1", 0, "Band"));

        TrackTags tags = read(Arrays.copyOf(tag, tag.length - 3));

        assertEquals("Song", tags.title());
    }

    @Test
    void readsTheAttachedPicture() {
        byte[] tag = TagFixtures.id3Tag(3, 0, TagFixtures.id3Apic(3, 3, TagFixtures.JPEG));

        assertTrue(read(tag).hasCover());
        assertArrayEquals(TagFixtures.JPEG,
                TagFormat.ID3V2.readPicture(ByteSource.ofBytes(tag)).orElseThrow());
    }

    @Test
    void version22IsIgnoredWithoutFailing() {
        byte[] tag = TagFixtures.concat("ID3".getBytes(StandardCharsets.US_ASCII), new byte[] {2, 0, 0},
                TagFixtures.synchsafe(20), new byte[20]);

        assertEquals(TrackTags.EMPTY, read(tag));
    }
}
