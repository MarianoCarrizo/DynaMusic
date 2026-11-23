package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FlacTagReaderTest {

    private static TrackTags read(byte[] bytes) {
        return TagFormat.FLAC.readTags(ByteSource.ofBytes(bytes));
    }

    @Test
    void readsTaggedFile() {
        TrackTags tags = read(TagFixtures.taggedFlac("TITLE=Song", "ARTIST=Band", "ALBUM=Rec", "TRACKNUMBER=3",
                "DATE=2020-05-01"));

        assertEquals("Song", tags.title());
        assertEquals("Band", tags.artist());
        assertEquals("Rec", tags.album());
        assertEquals(3, tags.trackNumber());
        assertEquals("2020", tags.year());
        assertFalse(tags.hasCover());
    }

    @Test
    void keysAreCaseInsensitiveAndFirstValueWins() {
        TrackTags tags = read(TagFixtures.taggedFlac("title=First", "Title=Second", "year=1999"));

        assertEquals("First", tags.title());
        assertEquals("1999", tags.year());
    }

    @Test
    void multipleArtistsAreJoined() {
        assertEquals("A, B", read(TagFixtures.taggedFlac("ARTIST=A", "ARTIST=B")).artist());
    }

    @Test
    void trackNumberAcceptsCommonForms() {
        assertEquals(3, read(TagFixtures.taggedFlac("TRACKNUMBER=3/12")).trackNumber());
        assertEquals(3, read(TagFixtures.taggedFlac("TRACKNUMBER= 3 ")).trackNumber());
        assertEquals(3, read(TagFixtures.taggedFlac("TRACKNUMBER=03")).trackNumber());
        assertEquals(0, read(TagFixtures.taggedFlac("TRACKNUMBER=abc")).trackNumber());
    }

    @Test
    void controlCharactersAndBlankValuesAreCleaned() {
        TrackTags tags = read(TagFixtures.taggedFlac("TITLE=  Name\u0001  ", "ALBUM=   "));

        assertEquals("Name", tags.title());
        assertEquals("", tags.album());
    }

    @Test
    void truncatedInsideTheCommentBlockKeepsWhatWasParsed() {
        byte[] full = TagFixtures.taggedFlac("TITLE=Song", "ARTIST=Band");

        TrackTags tags = read(Arrays.copyOf(full, full.length - 5));

        assertEquals("", tags.artist());
    }

    @Test
    void lyingBlockLengthStopsParsing() {
        byte[] header = {'f', 'L', 'a', 'C', (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1, 2, 3};

        assertEquals(TrackTags.EMPTY, read(header));
    }

    @Test
    void randomBytesAreEmpty() {
        byte[] noise = new byte[4096];
        new Random(7).nextBytes(noise);

        assertEquals(TrackTags.EMPTY, read(noise));
    }

    @Test
    void frontCoverIsPreferredOverOtherPictures() {
        byte[] other = TagFixtures.pictureBlock(0, TagFixtures.PNG);
        byte[] front = TagFixtures.pictureBlock(3, TagFixtures.JPEG);
        byte[] file = TagFixtures.flac(TagFixtures.flacBlock(0, false, new byte[34]),
                TagFixtures.flacBlock(6, false, other), TagFixtures.flacBlock(6, true, front));

        Optional<byte[]> picture = TagFormat.FLAC.readPicture(ByteSource.ofBytes(file));

        assertTrue(TagFormat.FLAC.readTags(ByteSource.ofBytes(file)).hasCover());
        assertArrayEquals(TagFixtures.JPEG, picture.orElseThrow());
    }

    @Test
    void fileWithoutPicturesHasNoCover() {
        assertTrue(TagFormat.FLAC.readPicture(ByteSource.ofBytes(TagFixtures.taggedFlac("TITLE=x"))).isEmpty());
    }
}
