package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class ExclusiveFallbackSinkTest {

    private static final AudioFormat HI_RES = PcmFormats.signedLittleEndian(24, 96_000, 2);
    private static final AudioFormat CD = PcmFormats.signedLittleEndian(16, 44_100, 2);
    private static final long SHORT_MILLIS = 50;
    private static final byte[] BLOCK = new byte[24];

    private final OutputArbiter arbiter = new OutputArbiter();
    private final FakeSharedLines lines = new FakeSharedLines();
    private FakeExclusiveSink exclusive = new FakeExclusiveSink(HI_RES);
    private ExclusiveFallbackSink sink = new ExclusiveFallbackSink(exclusive, lines, arbiter, SHORT_MILLIS);

    private void useFormat(AudioFormat format) {
        exclusive = new FakeExclusiveSink(format);
        sink = new ExclusiveFallbackSink(exclusive, lines, arbiter, SHORT_MILLIS);
    }

    @Test
    void theSinkReportsTheSourceFormatAndAnExclusiveStatus() {
        assertEquals(HI_RES, sink.format());
        assertEquals("24/96 exclusive", sink.status().label());
    }

    @Test
    void startingTakesTheExclusiveDeviceAndWritesGoToIt() {
        sink.start();
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(1, exclusive.starts());
        assertEquals(1, exclusive.writes());
        assertTrue(arbiter.holdsExclusive());
        assertTrue(lines.opened().isEmpty());
    }

    @Test
    void aBusyDeviceAtStartFallsBackToSharedWithoutAnError() {
        exclusive.failStartWith(new OutputUnavailableException("device in use"));
        lines.supportOnly(format -> true);

        sink.start();
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(1, lines.last().starts());
        assertEquals(BLOCK.length, lines.last().bytes().length);
        assertEquals("24/96 shared", sink.status().label());
        assertFalse(arbiter.holdsExclusive());
    }

    @Test
    void sharedFallbackOfAWideSourceReducesToSixteenBits() {
        exclusive.failStartWith(new OutputUnavailableException("device in use"));
        lines.supportOnly(format -> format.getSampleSizeInBits() == 16);

        sink.start();
        int accepted = sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(BLOCK.length, accepted);
        assertEquals(16, lines.last().format().getSampleSizeInBits());
        assertEquals("16/96 shared", sink.status().label());
        assertEquals(24, sink.format().getSampleSizeInBits());
    }

    @Test
    void sharedFallbackKeepsTheSourceFormatWhenTheLineSupportsIt() {
        useFormat(CD);
        exclusive.failStartWith(new OutputUnavailableException("not allowed"));

        sink.start();

        assertEquals(CD, lines.last().format());
        assertEquals("16/44.1 shared", sink.status().label());
    }

    @Test
    void aHolderThatNeverLetsGoSendsTheNextStreamToShared() {
        arbiter.claimExclusive(new Object(), SHORT_MILLIS);

        sink.start();

        assertEquals(0, exclusive.starts());
        assertEquals(1, lines.last().starts());
    }

    @Test
    void sharedPlaybackInProgressSendsTheStreamStraightToShared() {
        arbiter.sharedStarting(new Object(), SHORT_MILLIS);

        sink.start();

        assertEquals(0, exclusive.starts());
        assertEquals(1, lines.last().starts());
    }

    @Test
    void aMoodStartingMidTrackReleasesTheDeviceAndKeepsPlayingShared() {
        sink.start();
        sink.write(BLOCK, 0, BLOCK.length);
        exclusive.playFrames(100);

        arbiter.sharedStarting(new Object(), SHORT_MILLIS);
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(1, exclusive.suspends());
        assertFalse(arbiter.holdsExclusive());
        assertEquals(1, lines.last().starts());
        assertEquals(BLOCK.length, lines.last().bytes().length);
        assertEquals("24/96 shared", sink.status().label());
    }

    @Test
    void positionStaysContinuousAcrossTheSwitchToShared() {
        sink.start();
        exclusive.playFrames(500);
        arbiter.sharedStarting(new Object(), SHORT_MILLIS);

        sink.write(BLOCK, 0, BLOCK.length);
        lines.last().playFrames(30);

        assertEquals(530, sink.framePosition());
    }

    @Test
    void theStreamDoesNotReturnToExclusiveWhenSharedPlaybackEnds() {
        Object mood = new Object();
        sink.start();
        arbiter.sharedStarting(mood, SHORT_MILLIS);
        sink.write(BLOCK, 0, BLOCK.length);

        arbiter.sharedStopped(mood);
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(2 * BLOCK.length, lines.last().bytes().length);
        assertEquals(1, exclusive.starts());
        assertEquals(1, exclusive.suspends());
    }

    @Test
    void aFailingWriteMidTrackFallsBackOnceAndKeepsWriting() {
        sink.start();
        exclusive.failWritesWith(new OutputUnavailableException("device invalidated"));

        int accepted = sink.write(BLOCK, 0, BLOCK.length);
        sink.write(BLOCK, 0, BLOCK.length);

        assertEquals(BLOCK.length, accepted);
        assertEquals(1, lines.opened().size());
        assertEquals(2 * BLOCK.length, lines.last().bytes().length);
        assertFalse(arbiter.holdsExclusive());
    }

    @Test
    void aFailureWhileStoppingFallsBackInsteadOfThrowing() {
        sink.start();
        exclusive.failStopWith(new OutputUnavailableException("device invalidated"));

        sink.stop();

        assertEquals(1, lines.opened().size());
        assertEquals(0, lines.last().starts());
    }

    @Test
    void anUnavailableSharedLineRaisesOneUnavailableException() {
        lines.becomeUnavailable();
        sink.start();
        exclusive.failWritesWith(new OutputUnavailableException("device invalidated"));

        assertThrows(OutputUnavailableException.class, () -> sink.write(BLOCK, 0, BLOCK.length));
    }

    @Test
    void suspendReleasesTheDeviceAndTheArbiter() {
        sink.start();

        sink.suspend();

        assertEquals(1, exclusive.suspends());
        assertFalse(arbiter.holdsExclusive());
    }

    @Test
    void resumeAfterSuspendReacquiresTheExclusiveDevice() {
        sink.start();
        sink.suspend();

        sink.start();

        assertEquals(2, exclusive.starts());
        assertTrue(arbiter.holdsExclusive());
        assertTrue(lines.opened().isEmpty());
    }

    @Test
    void aFailedReacquireContinuesSharedFromTheSamePosition() {
        sink.start();
        exclusive.playFrames(700);
        sink.suspend();
        exclusive.failStartWith(new OutputUnavailableException("device in use"));

        sink.start();

        assertEquals(700, sink.framePosition());
        assertEquals(1, lines.last().starts());
        assertEquals("24/96 shared", sink.status().label());
    }

    @Test
    void aFailingSuspendFallsBackToShared() {
        sink.start();
        exclusive.failSuspendWith(new OutputUnavailableException("device invalidated"));

        sink.suspend();

        assertEquals(1, lines.opened().size());
        assertEquals(0, lines.last().starts());
        assertFalse(arbiter.holdsExclusive());
    }

    @Test
    void suspendingAfterTheFallbackLeavesTheSharedLineOpen() {
        exclusive.failStartWith(new OutputUnavailableException("device in use"));
        sink.start();

        sink.suspend();

        assertEquals(0, lines.last().closes());
    }

    @Test
    void closeReleasesBothSinksAndTheArbiterAndIsSafeTwice() {
        sink.start();
        arbiter.sharedStarting(new Object(), SHORT_MILLIS);
        sink.write(BLOCK, 0, BLOCK.length);

        sink.close();
        sink.close();

        assertTrue(exclusive.closes() >= 1);
        assertTrue(lines.last().closed());
        assertFalse(arbiter.holdsExclusive());
    }
}
