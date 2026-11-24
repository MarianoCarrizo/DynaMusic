package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FlacFrameHeaderTest {

    private static final int FIXED_SYNC = 0xF8;
    private static final int VARIABLE_SYNC = 0xF9;
    private static final int BLOCK_4096_RATE_44100 = 0xC9;
    private static final int STEREO_16_BIT = 0x18;
    private static final int FRAME_NUMBER = 7;

    private static byte[] header(int sync, int numberByte) {
        byte[] bytes = {(byte) 0xFF, (byte) sync, (byte) BLOCK_4096_RATE_44100, (byte) STEREO_16_BIT,
                (byte) numberByte, 0, 0, 0};
        bytes[5] = (byte) Crc8.compute(bytes, 0, 5);
        return bytes;
    }

    @Test
    void fixedBlockHeaderMultipliesFrameNumberByBlockSize() {
        Optional<FlacFrameHeader> parsed = FlacFrameHeader.parse(header(FIXED_SYNC, FRAME_NUMBER), 0, 4096);

        assertEquals(FRAME_NUMBER * 4096L, parsed.orElseThrow().firstSample());
        assertEquals(6, parsed.orElseThrow().length());
    }

    @Test
    void variableBlockHeaderCarriesSampleNumber() {
        byte[] bytes = header(VARIABLE_SYNC, 0x7F);
        Optional<FlacFrameHeader> parsed = FlacFrameHeader.parse(bytes, 0, 4096);

        assertEquals(0x7F, parsed.orElseThrow().firstSample());
    }

    @Test
    void corruptCrcIsRejected() {
        byte[] bytes = header(FIXED_SYNC, FRAME_NUMBER);
        bytes[5] ^= 0x01;

        assertTrue(FlacFrameHeader.parse(bytes, 0, 4096).isEmpty());
    }

    @Test
    void wrongSyncIsRejected() {
        byte[] bytes = header(FIXED_SYNC, FRAME_NUMBER);
        bytes[1] = (byte) 0xFA;

        assertTrue(FlacFrameHeader.parse(bytes, 0, 4096).isEmpty());
    }

    @Test
    void truncatedHeaderIsRejected() {
        byte[] bytes = header(FIXED_SYNC, FRAME_NUMBER);

        assertTrue(FlacFrameHeader.parse(bytes, 3, 4096).isEmpty());
        assertTrue(FlacFrameHeader.parse(new byte[2], 0, 4096).isEmpty());
        assertTrue(FlacFrameHeader.parse(bytes, -1, 4096).isEmpty());
    }

    @Test
    void realFixtureFramesAreRecognisedAtTheirOffsets() throws Exception {
        File fixture = FlacFixtures.file(16, false);
        long firstFrame;
        try (RandomAccessFile file = new RandomAccessFile(fixture, "r")) {
            firstFrame = FlacMetadata.read(file).firstFrameOffset();
        }
        byte[] data = Files.readAllBytes(fixture.toPath());

        FlacFrameHeader first = FlacFrameHeader.parse(data, (int) firstFrame, FlacFixtures.BLOCK_SIZE).orElseThrow();

        assertEquals(0, first.firstSample());
        assertTrue(FlacFrameHeader.parse(data, (int) firstFrame + 1, FlacFixtures.BLOCK_SIZE).isEmpty());
    }
}
