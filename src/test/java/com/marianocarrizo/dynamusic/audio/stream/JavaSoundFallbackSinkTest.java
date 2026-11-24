package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class JavaSoundFallbackSinkTest {

    private static final AudioFormat HI_RES = PcmFormats.signedLittleEndian(24, 96_000, 2);
    private static final byte[] BLOCK = new byte[24];

    private final FakeSharedLines lines = new FakeSharedLines();
    private final FakeExclusiveSink primary = new FakeExclusiveSink(HI_RES);
    private final JavaSoundFallbackSink sink = new JavaSoundFallbackSink(primary, lines);

    @Test
    void theSinkReportsThePrimaryFormatAndStatus() {
        assertEquals(HI_RES, sink.format());
        assertEquals(OutputRoute.EXCLUSIVE, sink.status().route());
    }

    @Test
    void aWorkingPrimaryReceivesEverythingAndJavaSoundIsNeverOpened() {
        sink.start();
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(1, primary.starts());
        assertEquals(1, primary.writes());
        assertTrue(lines.opened().isEmpty());
    }

    @Test
    void aFailedStartFallsBackToJavaSoundAndStartsIt() {
        primary.failStartWith(new OutputUnavailableException("no device"));
        lines.supportOnly(format -> true);

        sink.start();
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(1, lines.last().starts());
        assertEquals(BLOCK.length, lines.last().bytes().length);
        assertEquals("24/96 shared", sink.status().label());
        assertEquals(1, primary.closes());
    }

    @Test
    void aFailedWriteSendsOnlyTheUnconsumedRemainderToJavaSound() {
        lines.supportOnly(format -> true);
        sink.start();
        primary.failWritesWith(new OutputUnavailableException("lost").consuming(6));

        int accepted = sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(BLOCK.length, accepted);
        assertEquals(BLOCK.length - 6, lines.last().bytes().length);
        assertEquals(1, lines.last().starts());
    }

    @Test
    void thePositionContinuesFromWhereThePrimaryStopped() {
        lines.supportOnly(format -> true);
        sink.start();
        primary.playFrames(500);
        primary.failWritesWith(new OutputUnavailableException("lost"));

        sink.write(BLOCK, 0, BLOCK.length);
        lines.last().playFrames(40);

        assertEquals(540, sink.framePosition());
    }

    @Test
    void aWideSourceIsReducedToSixteenBitsWhenJavaSoundCannotTakeIt() {
        primary.failStartWith(new OutputUnavailableException("no device"));
        lines.supportOnly(format -> format.getSampleSizeInBits() == 16);

        sink.start();

        assertEquals(16, lines.last().format().getSampleSizeInBits());
        assertEquals("16/96 shared", sink.status().label());
    }

    @Test
    void aFailureOnStopFallsBackInsteadOfEscaping() {
        lines.supportOnly(format -> true);
        sink.start();
        primary.failStopWith(new OutputUnavailableException("lost"));

        sink.stop();

        assertEquals(1, lines.opened().size());
    }

    @Test
    void noJavaSoundLineEitherRaisesUnavailable() {
        primary.failStartWith(new OutputUnavailableException("no device"));
        lines.becomeUnavailable();

        assertThrows(OutputUnavailableException.class, sink::start);
    }

    @Test
    void closeReleasesBothSinks() {
        lines.supportOnly(format -> true);
        primary.failStartWith(new OutputUnavailableException("no device"));
        sink.start();

        sink.close();

        assertEquals(2, primary.closes());
        assertEquals(1, lines.last().closes());
    }
}
