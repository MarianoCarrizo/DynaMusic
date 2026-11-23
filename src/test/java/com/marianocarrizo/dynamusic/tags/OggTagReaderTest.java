package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OggTagReaderTest {

    private static TrackTags read(byte[] bytes) {
        return TagFormat.OGG.readTags(ByteSource.ofBytes(bytes));
    }

    @Test
    void readsVorbisComments() {
        byte[] file = TagFixtures.ogg(TagFixtures.vorbisIdentification(),
                TagFixtures.vorbisCommentPacket("TITLE=Song", "ARTIST=Band", "ALBUM=Rec", "TRACKNUMBER=2", "DATE=2018"));

        TrackTags tags = read(file);

        assertEquals("Song", tags.title());
        assertEquals("Band", tags.artist());
        assertEquals("Rec", tags.album());
        assertEquals(2, tags.trackNumber());
        assertEquals("2018", tags.year());
    }

    @Test
    void readsOpusTags() {
        byte[] file = TagFixtures.ogg(TagFixtures.opusHead(), TagFixtures.opusTagsPacket("TITLE=Opus Song", "ARTIST=Band"));

        TrackTags tags = read(file);

        assertEquals("Opus Song", tags.title());
        assertEquals("Band", tags.artist());
    }

    @Test
    void reassemblesACommentPacketSpanningPages() {
        byte[] padding = new byte[600];
        Arrays.fill(padding, (byte) 'x');
        byte[] packet = TagFixtures.vorbisCommentPacket("TITLE=Long Song", "COMMENT=" + new String(padding));
        int firstPart = 255 * 2;
        byte[] first = TagFixtures.oggPage(Arrays.copyOfRange(packet, 0, firstPart), false, true);
        byte[] second = TagFixtures.oggPage(Arrays.copyOfRange(packet, firstPart, packet.length), true, false);
        byte[] file = TagFixtures.concat(TagFixtures.oggPage(TagFixtures.vorbisIdentification(), false, false), first,
                second);

        assertEquals("Long Song", read(file).title());
    }

    @Test
    void truncatedFileKeepsNothingAndDoesNotFail() {
        byte[] file = TagFixtures.ogg(TagFixtures.vorbisIdentification(), TagFixtures.vorbisCommentPacket("TITLE=Song"));

        assertEquals(TrackTags.EMPTY, read(Arrays.copyOf(file, file.length - 10)));
    }

    @Test
    void badCapturePatternIsEmpty() {
        byte[] file = TagFixtures.ogg(TagFixtures.vorbisIdentification(), TagFixtures.vorbisCommentPacket("TITLE=Song"));
        file[0] = 'X';

        assertEquals(TrackTags.EMPTY, read(file));
    }

    @Test
    void checksumsAreNotRequired() {
        byte[] file = TagFixtures.ogg(TagFixtures.vorbisIdentification(), TagFixtures.vorbisCommentPacket("TITLE=Song"));

        assertEquals("Song", read(file).title());
    }

    @Test
    void embeddedPictureIsDecodedFromBase64() {
        byte[] file = TagFixtures.ogg(TagFixtures.vorbisIdentification(),
                TagFixtures.vorbisCommentPacket("TITLE=Song", TagFixtures.pictureField(3, TagFixtures.PNG)));

        assertTrue(read(file).hasCover());
        assertArrayEquals(TagFixtures.PNG, TagFormat.OGG.readPicture(ByteSource.ofBytes(file)).orElseThrow());
    }

    @Test
    void invalidBase64PictureKeepsTextTags() {
        byte[] file = TagFixtures.ogg(TagFixtures.vorbisIdentification(),
                TagFixtures.vorbisCommentPacket("TITLE=Song", "METADATA_BLOCK_PICTURE=%%%not-base64%%%"));

        TrackTags tags = read(file);

        assertEquals("Song", tags.title());
        assertFalse(tags.hasCover());
    }
}
