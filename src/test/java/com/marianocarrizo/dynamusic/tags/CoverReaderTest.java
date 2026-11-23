package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverReaderTest {

    @TempDir Path dir;

    @Test
    void jpegAndPngAreAccepted() {
        assertArrayEquals(TagFixtures.JPEG, CoverReader.validated(Optional.of(TagFixtures.JPEG)).orElseThrow());
        assertArrayEquals(TagFixtures.PNG, CoverReader.validated(Optional.of(TagFixtures.PNG)).orElseThrow());
    }

    @Test
    void otherBytesAreRejected() {
        assertTrue(CoverReader.validated(Optional.of(new byte[] {1, 2, 3, 4})).isEmpty());
        assertTrue(CoverReader.validated(Optional.of(new byte[0])).isEmpty());
        assertTrue(CoverReader.validated(Optional.empty()).isEmpty());
    }

    @Test
    void readsTheEmbeddedCoverFromARealFile() throws IOException {
        Path file = dir.resolve("song.flac");
        Files.write(file, TagFixtures.flac(TagFixtures.flacBlock(0, false, new byte[34]),
                TagFixtures.flacBlock(6, true, TagFixtures.pictureBlock(3, TagFixtures.JPEG))));

        assertArrayEquals(TagFixtures.JPEG, CoverReader.read(file.toFile(), TagFormat.FLAC).orElseThrow());
    }

    @Test
    void invalidEmbeddedImageIsRejected() throws IOException {
        Path file = dir.resolve("song.flac");
        Files.write(file, TagFixtures.flac(TagFixtures.flacBlock(0, false, new byte[34]),
                TagFixtures.flacBlock(6, true, TagFixtures.pictureBlock(3, new byte[] {1, 2, 3, 4, 5}))));

        assertTrue(CoverReader.read(file.toFile(), TagFormat.FLAC).isEmpty());
    }

    @Test
    void missingFileIsEmpty() {
        assertTrue(CoverReader.read(dir.resolve("none.flac").toFile(), TagFormat.FLAC).isEmpty());
    }
}
