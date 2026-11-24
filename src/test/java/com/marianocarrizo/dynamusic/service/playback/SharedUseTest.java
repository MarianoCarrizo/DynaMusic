package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SharedUseTest {

    private final OutputArbiter arbiter = new OutputArbiter();
    private final Object holder = new Object();
    private final Object playback = new Object();
    private final AtomicInteger plays = new AtomicInteger();
    private final SharedUse use = new SharedUse(arbiter, playback, Runnable::run);

    @Test
    void startingRegistersSharedPlaybackAndPlaysWhenTheDeviceIsFree() {
        use.start(plays::incrementAndGet);

        assertTrue(arbiter.sharedInUse());
        assertEquals(1, plays.get());
    }

    @Test
    void startingWhileTheDeviceIsHeldPlaysOnlyAfterTheRelease() {
        arbiter.claimExclusive(holder, 1);

        use.start(plays::incrementAndGet);
        assertEquals(0, plays.get());
        arbiter.releaseExclusive(holder);

        assertEquals(1, plays.get());
    }

    @Test
    void stoppingBeforeTheReleaseNeverPlays() {
        arbiter.claimExclusive(holder, 1);
        use.start(plays::incrementAndGet);

        use.stop();
        arbiter.releaseExclusive(holder);

        assertEquals(0, plays.get());
        assertFalse(arbiter.sharedInUse());
    }

    @Test
    void aLatePausedEventAfterPlayingAgainKeepsThePlayerRegistered() {
        use.start(plays::incrementAndGet);
        use.stop();
        use.start(plays::incrementAndGet);

        use.observed(PlaybackStatus.PAUSED);

        assertTrue(arbiter.sharedInUse());
        assertFalse(arbiter.claimExclusive(holder, 1));
    }

    @Test
    void aSilentStatusWithoutAPlayRequestUnregistersThePlayer() {
        use.start(plays::incrementAndGet);
        use.stop();
        use.start(plays::incrementAndGet);
        use.observed(PlaybackStatus.PAUSED);

        use.stop();
        use.observed(PlaybackStatus.PAUSED);

        assertFalse(arbiter.sharedInUse());
    }

    @Test
    void aHaltedPlayerAlwaysUnregisters() {
        use.start(plays::incrementAndGet);

        use.observed(PlaybackStatus.HALTED);

        assertFalse(arbiter.sharedInUse());
    }

    @Test
    void aPlayingStatusKeepsThePlayerRegistered() {
        use.start(plays::incrementAndGet);

        use.observed(PlaybackStatus.PLAYING);

        assertTrue(arbiter.sharedInUse());
    }
}
