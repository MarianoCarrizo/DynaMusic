package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TagReadersRobustnessTest {

    private static final int MUTATIONS = 300;

    private static byte[][] validFiles() {
        return new byte[][] {
            TagFixtures.taggedFlac("TITLE=Song", "ARTIST=Band"),
            TagFixtures.id3Tag(3, 0, TagFixtures.id3Text(3, "TIT2", 0, "Song"),
                    TagFixtures.id3Apic(3, 3, TagFixtures.JPEG)),
            TagFixtures.ogg(TagFixtures.vorbisIdentification(), TagFixtures.vorbisCommentPacket("TITLE=Song")),
            TagFixtures.ogg(TagFixtures.opusHead(), TagFixtures.opusTagsPacket("TITLE=Song")),
            TagFixtures.mp4File(TagFixtures.mp4Meta(TagFixtures.textItem("nam", "Song"),
                    TagFixtures.coverItem(13, TagFixtures.JPEG)), true)
        };
    }

    private static final TagFormat[] FORMATS = {
        TagFormat.FLAC, TagFormat.ID3V2, TagFormat.OGG, TagFormat.OGG, TagFormat.MP4
    };

    @Test
    void everyReaderSurvivesEveryTruncation() {
        byte[][] files = validFiles();
        for (int i = 0; i < files.length; i++) {
            for (int length = 0; length <= files[i].length; length++) {
                byte[] cut = Arrays.copyOf(files[i], length);
                TagFormat format = FORMATS[i];
                assertDoesNotThrow(() -> format.readTags(ByteSource.ofBytes(cut)));
                assertDoesNotThrow(() -> format.readPicture(ByteSource.ofBytes(cut)));
            }
        }
    }

    @Test
    void everyReaderSurvivesRandomMutations() {
        Random random = new Random(42);
        byte[][] files = validFiles();
        for (int i = 0; i < files.length; i++) {
            for (int round = 0; round < MUTATIONS; round++) {
                byte[] mutated = files[i].clone();
                int flips = 1 + random.nextInt(6);
                for (int flip = 0; flip < flips; flip++) mutated[random.nextInt(mutated.length)] = (byte) random.nextInt();
                TagFormat format = FORMATS[i];
                assertDoesNotThrow(() -> format.readTags(ByteSource.ofBytes(mutated)));
                assertDoesNotThrow(() -> format.readPicture(ByteSource.ofBytes(mutated)));
            }
        }
    }

    @Test
    void everyReaderReturnsEmptyTagsForGarbage() {
        byte[] garbage = new byte[2048];
        new Random(1).nextBytes(garbage);
        for (TagFormat format : FORMATS) {
            assertEquals(TrackTags.EMPTY, format.readTags(ByteSource.ofBytes(garbage)));
        }
    }

    @Test
    void formatWithoutReaderIsAlwaysEmpty() {
        assertEquals(TrackTags.EMPTY, TagFormat.NONE.readTags(ByteSource.ofBytes(validFiles()[0])));
    }
}
