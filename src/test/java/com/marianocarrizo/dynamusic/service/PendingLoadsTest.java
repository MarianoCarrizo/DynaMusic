package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.pcm.FfmpegConverter;
import com.marianocarrizo.dynamusic.audio.pcm.FfmpegGainProbe;
import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.LoadState;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.tags.TrackTags;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PendingLoadsTest {

    private static final File SONG = new File("music/Final Fantasy VII/One-Winged Angel.flac");

    private final AtomicLong now = new AtomicLong(1000);
    private final PendingLoads loads = new PendingLoads(now::get);

    private Optional<PendingLoad> current() {
        return loads.current(file -> Optional.empty());
    }

    @Test
    void nothingIsPendingInitially() {
        assertTrue(current().isEmpty());
        assertFalse(loads.isActive());
    }

    @Test
    void aRequestShowsTheRequestedTrackAsLoadingImmediately() {
        loads.begin(NowPlayingSource.ON_DEMAND, null, SONG, null);

        PendingLoad pending = current().orElseThrow();

        assertEquals(NowPlayingSource.ON_DEMAND, pending.source());
        assertEquals("One-Winged Angel", pending.track().title());
        assertEquals("Final Fantasy VII", pending.track().detail());
        assertEquals(LoadState.LOADING, pending.track().state());
        assertFalse(pending.track().playing());
        assertTrue(loads.isActive());
    }

    @Test
    void tagsRefineTheLabelsWhileLoading() {
        loads.begin(NowPlayingSource.PREVIEW, null, SONG, null);
        TrackTags tags = new TrackTags("One-Winged Angel", "Nobuo Uematsu", "Advent Children", 1, "2005", false);

        PendingLoad pending = loads.current(file -> Optional.of(tags)).orElseThrow();

        assertEquals("Nobuo Uematsu · Advent Children", pending.track().detail());
    }

    @Test
    void anExplicitTitleWinsOverTheFileName() {
        loads.begin(NowPlayingSource.ON_DEMAND, null, SONG, "From YouTube");

        assertEquals("From YouTube", current().orElseThrow().track().title());
    }

    @Test
    void finishingTheLatestRequestClearsIt() {
        int ticket = loads.begin(NowPlayingSource.PLAYLIST, null, SONG, null);

        assertTrue(loads.finish(ticket));

        assertTrue(current().isEmpty());
    }

    @Test
    void aNewerRequestSupersedesAndTheOlderOneCannotClearIt() {
        int older = loads.begin(NowPlayingSource.PLAYLIST, null, new File("a.mp3"), null);
        int newer = loads.begin(NowPlayingSource.ON_DEMAND, null, SONG, null);

        assertFalse(loads.finish(older));
        assertFalse(loads.fail(older, "late failure"));

        assertEquals(NowPlayingSource.ON_DEMAND, current().orElseThrow().source());
        assertTrue(loads.finish(newer));
    }

    @Test
    void failureIsShownWithItsMessageThenExpires() {
        int ticket = loads.begin(NowPlayingSource.ON_DEMAND, null, SONG, null);

        assertTrue(loads.fail(ticket, "FLAC could not be decoded."));

        PendingLoad failed = current().orElseThrow();
        assertEquals(LoadState.FAILED, failed.track().state());
        assertEquals("FLAC could not be decoded.", failed.track().detail());
        now.addAndGet(PendingLoads.FAILURE_DISPLAY_MILLIS - 1);
        assertTrue(current().isPresent());
        now.addAndGet(1);
        assertTrue(current().isEmpty());
    }

    @Test
    void aFailedRequestIsNotClearedByAnEarlyFinish() {
        int ticket = loads.begin(NowPlayingSource.PLAYLIST, null, SONG, null);
        loads.fail(ticket, "boom");

        assertFalse(loads.finish(ticket));

        assertEquals(LoadState.FAILED, current().orElseThrow().track().state());
    }

    @Test
    void aStuckLoadEventuallyGivesUp() {
        loads.begin(NowPlayingSource.PREVIEW, null, SONG, null);

        now.addAndGet(PendingLoads.LOADING_TIMEOUT_MILLIS);

        assertTrue(current().isEmpty());
    }

    @Test
    void theLoadingTimeoutOutlastsTheLongestPreparation() {
        long longestPreparationMillis = (FfmpegConverter.TIMEOUT_SECONDS + FfmpegGainProbe.TIMEOUT_SECONDS) * 1000;

        assertTrue(PendingLoads.LOADING_TIMEOUT_MILLIS > longestPreparationMillis);
    }

    @Test
    void aSlowLoadIsStillShownJustBeforeTheTimeout() {
        loads.begin(NowPlayingSource.ON_DEMAND, null, SONG, null);

        now.addAndGet(PendingLoads.LOADING_TIMEOUT_MILLIS - 1);

        assertTrue(current().isPresent());
    }

    @Test
    void aLoadThatFinishesAfterExpiryDoesNotResurrectAnything() {
        int ticket = loads.begin(NowPlayingSource.ON_DEMAND, null, SONG, null);
        now.addAndGet(PendingLoads.LOADING_TIMEOUT_MILLIS);
        assertTrue(current().isEmpty());

        assertFalse(loads.finish(ticket));

        assertTrue(current().isEmpty());
    }

    @Test
    void moodRequestsCarryTheirSlot() {
        loads.begin(NowPlayingSource.MOOD, TrackType.RAGED, SONG, null);

        assertEquals(TrackType.RAGED, current().orElseThrow().moodType());
    }
}
