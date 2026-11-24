package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PositionClockTest {

    @Test
    void advancesWithTheSinkFramePosition() {
        PositionClock clock = new PositionClock(48_000, 48_000);
        clock.rebase(0, 0);

        assertEquals(48_000, clock.sourceFrame(48_000));
        assertEquals(1.0, clock.seconds(48_000), 1e-9);
    }

    @Test
    void seekBaseIsAddedToElapsedFramesSinceTheMark() {
        PositionClock clock = new PositionClock(44_100, 44_100);
        clock.rebase(441_000, 100_000);

        assertEquals(441_000, clock.sourceFrame(100_000));
        assertEquals(441_000 + 22_050, clock.sourceFrame(122_050));
        assertEquals(10.5, clock.seconds(122_050), 1e-9);
    }

    @Test
    void rebasingAfterAFlushIgnoresTheOldSinkPosition() {
        PositionClock clock = new PositionClock(44_100, 44_100);
        clock.rebase(0, 0);
        clock.rebase(88_200, 30_000);

        assertEquals(88_200, clock.sourceFrame(30_000));
    }

    @Test
    void doesNotAdvanceWhilePausedAndContinuesFromTheSamePosition() {
        PositionClock clock = new PositionClock(44_100, 44_100);
        clock.rebase(0, 0);
        clock.pause(44_100);

        assertEquals(44_100, clock.sourceFrame(44_100));
        assertEquals(44_100, clock.sourceFrame(90_000));

        clock.resume(90_000);

        assertEquals(44_100, clock.sourceFrame(90_000));
        assertEquals(44_100 + 100, clock.sourceFrame(90_100));
    }

    @Test
    void timeIsMeasuredAtTheSourceRateWhenTheSinkRateDiffers() {
        PositionClock clock = new PositionClock(96_000, 48_000);
        clock.rebase(0, 0);

        assertEquals(96_000, clock.sourceFrame(48_000));
        assertEquals(1.0, clock.seconds(48_000), 1e-9);
    }

    @Test
    void reducingBitDepthDoesNotChangeTheReportedTime() {
        PositionClock clock = new PositionClock(96_000, 96_000);
        clock.rebase(0, 0);

        assertEquals(5.0, clock.seconds(480_000), 1e-9);
    }

    @Test
    void neverReportsAPositionBeforeTheBase() {
        PositionClock clock = new PositionClock(44_100, 44_100);
        clock.rebase(1_000, 500);

        assertEquals(1_000, clock.sourceFrame(200));
    }
}
