package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OutputArbiterTest {

    private static final long SHORT_MILLIS = 50;
    private static final long LONG_MILLIS = 5000;

    private final OutputArbiter arbiter = new OutputArbiter();
    private final Object ours = new Object();
    private final Object theirs = new Object();

    @Test
    void anIdleDeviceCanBeClaimed() {
        assertTrue(arbiter.claimExclusive(ours, SHORT_MILLIS));
        assertTrue(arbiter.holdsExclusive());
    }

    @Test
    void claimingAgainAsTheHolderSucceeds() {
        arbiter.claimExclusive(ours, SHORT_MILLIS);

        assertTrue(arbiter.claimExclusive(ours, SHORT_MILLIS));
    }

    @Test
    void anotherHolderTimesOutWhileTheDeviceIsHeld() {
        arbiter.claimExclusive(ours, SHORT_MILLIS);

        assertFalse(arbiter.claimExclusive(theirs, SHORT_MILLIS));
    }

    @Test
    void aWaitingClaimSucceedsWhenTheHolderReleases() throws Exception {
        arbiter.claimExclusive(ours, SHORT_MILLIS);
        CompletableFuture<Boolean> waiting = CompletableFuture.supplyAsync(
                () -> arbiter.claimExclusive(theirs, LONG_MILLIS));

        arbiter.releaseExclusive(ours);

        assertTrue(waiting.get(LONG_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test
    void onlyTheHolderCanRelease() {
        arbiter.claimExclusive(ours, SHORT_MILLIS);

        arbiter.releaseExclusive(theirs);

        assertTrue(arbiter.holdsExclusive());
    }

    @Test
    void sharedPlaybackBlocksNewClaimsImmediately() {
        arbiter.sharedStarting(theirs, SHORT_MILLIS);

        assertTrue(arbiter.sharedInUse());
        assertFalse(arbiter.claimExclusive(ours, LONG_MILLIS));
    }

    @Test
    void stoppingSharedPlaybackAllowsClaimsAgain() {
        arbiter.sharedStarting(theirs, SHORT_MILLIS);
        arbiter.sharedStopped(theirs);

        assertFalse(arbiter.sharedInUse());
        assertTrue(arbiter.claimExclusive(ours, SHORT_MILLIS));
    }

    @Test
    void sharedUsersAreCountedIndividually() {
        Object another = new Object();
        arbiter.sharedStarting(theirs, SHORT_MILLIS);
        arbiter.sharedStarting(another, SHORT_MILLIS);

        arbiter.sharedStopped(theirs);

        assertTrue(arbiter.sharedInUse());
    }

    @Test
    void sharedStartingReturnsAtOnceWhenNobodyHoldsTheDevice() {
        assertTrue(arbiter.sharedStarting(theirs, SHORT_MILLIS));
    }

    @Test
    void sharedStartingGivesUpWhenTheHolderDoesNotRelease() {
        arbiter.claimExclusive(ours, SHORT_MILLIS);

        assertFalse(arbiter.sharedStarting(theirs, SHORT_MILLIS));
        assertTrue(arbiter.sharedInUse());
    }

    @Test
    void sharedStartingReturnsWhenTheHolderReleasesInTime() throws Exception {
        arbiter.claimExclusive(ours, SHORT_MILLIS);
        CompletableFuture<Boolean> starting = CompletableFuture.supplyAsync(
                () -> arbiter.sharedStarting(theirs, LONG_MILLIS));
        while (!arbiter.sharedInUse()) {
            Thread.onSpinWait();
        }

        arbiter.releaseExclusive(ours);

        assertTrue(starting.get(LONG_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test
    void theProcessArbiterIsShared() {
        assertSame(OutputArbiter.process(), OutputArbiter.process());
    }
}
