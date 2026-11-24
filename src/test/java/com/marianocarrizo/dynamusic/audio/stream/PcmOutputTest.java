package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class PcmOutputTest {

    private static final float RATE = 44_100f;
    private static final AudioFormat PCM16 = PcmFormats.signedLittleEndian(16, RATE, 2);
    private static final AudioFormat PCM24 = PcmFormats.signedLittleEndian(24, RATE, 2);

    private final FakePcmSink sink16 = new FakePcmSink(PCM16);

    @Test
    void writesTheBlockUnchangedAtFullVolume() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);
        byte[] block = PcmBytes.pack(SampleWidth.BITS_16, 100, -100, 200, -200);

        int frames = output.write(block.clone(), block.length);

        assertEquals(2, frames);
        assertArrayEquals(block, sink16.bytes());
    }

    @Test
    void scalesOnlyByTheVolumeWithNoNormalization() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 0.5);
        byte[] block = PcmBytes.pack(SampleWidth.BITS_16, 1000, -2000, 3000, -4000);

        output.write(block, block.length);

        assertArrayEquals(new int[] {500, -1000, 1500, -2000}, sink16.samples(SampleWidth.BITS_16));
    }

    @Test
    void volumeChangesRampDownwardAcrossBlocks() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);
        int[] constant = new int[882];
        Arrays.fill(constant, 10_000);
        int previous = Integer.MAX_VALUE;

        for (int step = 1; step <= 10; step++) {
            output.setVolume(1.0 - step / 10.0);
            byte[] block = PcmBytes.pack(SampleWidth.BITS_16, constant);
            output.write(block, block.length);
        }
        for (int sample : sink16.samples(SampleWidth.BITS_16)) {
            assertTrue(sample <= previous);
            previous = sample;
        }

        assertEquals(0, previous);
    }

    @Test
    void reducesTwentyFourBitToSixteenForASixteenBitSink() {
        PcmOutput output = new PcmOutput(sink16, PCM24, RATE, 1.0);
        byte[] block = PcmBytes.pack(SampleWidth.BITS_24, 0x7FFFFF, -0x800000, 256, -256);

        int frames = output.write(block, block.length);

        assertEquals(2, frames);
        assertArrayEquals(new int[] {32767, -32768, 1, -1}, sink16.samples(SampleWidth.BITS_16));
    }

    @Test
    void passesTwentyFourBitThroughToATwentyFourBitSink() {
        FakePcmSink sink24 = new FakePcmSink(PCM24);
        PcmOutput output = new PcmOutput(sink24, PCM24, RATE, 1.0);
        byte[] block = PcmBytes.pack(SampleWidth.BITS_24, 123_456, -123_456);

        output.write(block.clone(), block.length);

        assertArrayEquals(block, sink24.bytes());
    }

    @Test
    void dropsATrailingPartialFrame() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);
        byte[] block = new byte[6];

        assertEquals(1, output.write(block, block.length));
    }

    @Test
    void positionFollowsTheSinkFramePositionAtTheSourceRate() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);

        sink16.playFrames(44_100);

        assertEquals(1.0, output.seconds(), 1e-9);
        assertEquals(44_100, output.sourceFrame());
    }

    @Test
    void seekFlushesTheSinkAndRebasesThePosition() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);
        sink16.playFrames(10_000);

        output.seek(88_200);

        assertEquals(1, sink16.flushes());
        assertEquals(88_200, output.sourceFrame());
        sink16.playFrames(4_410);
        assertEquals(2.1, output.seconds(), 1e-9);
    }

    @Test
    void positionStandsStillWhilePausedAndResumesFromTheSameFrame() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);
        sink16.playFrames(22_050);
        output.pause();
        sink16.playFrames(10_000);

        assertEquals(22_050, output.sourceFrame());

        output.start();
        sink16.playFrames(441);

        assertEquals(22_491, output.sourceFrame());
    }

    @Test
    void closeAndDrainReachTheSink() {
        PcmOutput output = new PcmOutput(sink16, PCM16, RATE, 1.0);

        output.drain();
        output.close();

        assertEquals(1, sink16.drains());
        assertTrue(sink16.closed());
    }
}
