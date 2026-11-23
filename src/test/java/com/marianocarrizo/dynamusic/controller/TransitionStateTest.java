package com.marianocarrizo.dynamusic.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.LoadState;
import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.service.MusicService.PlaybackMode;
import com.marianocarrizo.dynamusic.service.PendingLoad;
import com.marianocarrizo.dynamusic.service.PlaybackState;
import com.marianocarrizo.dynamusic.service.SoundingTrack;
import java.io.File;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransitionStateTest {

    private static final File TRAM_FILE = new File("music/Suburban Tram.flac");
    private static final File ANGEL_FILE = new File("library/One-Winged Angel.flac");
    private static final SoundingTrack TRAM = new SoundingTrack("Suburban Tram", "Album", TRAM_FILE, true);

    private static PlaybackState playlistPlaying(Optional<PendingLoad> pending) {
        return new PlaybackState(PlaybackMode.PLAYLIST, false, TrackType.NORMAL, SoundingTrack.ABSENT,
                SoundingTrack.ABSENT, TRAM, SoundingTrack.ABSENT, pending);
    }

    private static PendingLoad loading(NowPlayingSource source, File file, String title) {
        SoundingTrack requested = new SoundingTrack(title, "", file, false, LoadState.LOADING);
        return new PendingLoad(source, null, requested);
    }

    @Test
    void whileTheNewTrackLoadsTheBarShowsItInsteadOfThePreviousOne() {
        PlaybackState state = playlistPlaying(Optional.of(loading(NowPlayingSource.ON_DEMAND, ANGEL_FILE,
                "One-Winged Angel")));

        NowPlaying shown = NowPlayingTracker.decide(state, "mine");

        assertEquals("One-Winged Angel", shown.title());
        assertEquals(NowPlayingSource.ON_DEMAND, shown.source());
        assertEquals(ANGEL_FILE, shown.file());
        assertTrue(shown.isLoading());
        assertFalse(shown.playing());
    }

    @Test
    void thePreviousPlaylistRowStopsBeingHighlightedWhileAnotherSourceLoads() {
        PlaybackState state = playlistPlaying(Optional.of(loading(NowPlayingSource.PREVIEW, ANGEL_FILE, "Angel")));

        NowPlaying shown = NowPlayingTracker.decide(state, "mine");
        boolean playlistRowIsCurrent = shown.source() == NowPlayingSource.PLAYLIST;

        assertEquals(RowPlayback.NONE, RowPlayback.of(playlistRowIsCurrent, shown.playing(), shown.isLoading()));
    }

    @Test
    void aLoadingPlaylistTrackMarksItsOwnRowAsLoading() {
        PlaybackState state = playlistPlaying(Optional.of(loading(NowPlayingSource.PLAYLIST, ANGEL_FILE, "Angel")));

        NowPlaying shown = NowPlayingTracker.decide(state, "mine");

        assertEquals("mine", shown.sourceName());
        assertEquals(RowPlayback.LOADING, RowPlayback.of(true, shown.playing(), shown.isLoading()));
        assertEquals("…", RowPlayback.LOADING.marker());
    }

    @Test
    void moodRequestsCarryTheMoodName() {
        SoundingTrack requested = new SoundingTrack("Battle", "", ANGEL_FILE, false, LoadState.LOADING);
        PendingLoad load = new PendingLoad(NowPlayingSource.MOOD, TrackType.RAGED, requested);

        NowPlaying shown = NowPlayingTracker.decide(playlistPlaying(Optional.of(load)), "mine");

        assertEquals("RAGED", shown.sourceName());
    }

    @Test
    void onlyPlayPauseAndSeekAreBlockedWhilePreparing() {
        NowPlaying loading = NowPlayingTracker.decide(playlistPlaying(Optional.of(
                loading(NowPlayingSource.PLAYLIST, ANGEL_FILE, "Angel"))), "mine");
        SoundingTrack failedTrack = new SoundingTrack("Angel", "ogg needs ffmpeg", ANGEL_FILE, false, LoadState.FAILED);
        NowPlaying failed = NowPlayingTracker.decide(playlistPlaying(Optional.of(
                new PendingLoad(NowPlayingSource.PLAYLIST, null, failedTrack))), "mine");

        assertEquals(new TransportAvailability(true, true, true, true), TransportAvailability.of(loading.source()));
        assertTrue(loading.isBusy());
        assertEquals("ogg needs ffmpeg", failed.detail());
        assertTrue(failed.isBusy());
        assertFalse(failed.isLoading());
    }

    @Test
    void readyTracksKeepTheNormalTransport() {
        NowPlaying ready = NowPlayingTracker.decide(playlistPlaying(Optional.empty()), "mine");

        assertEquals(new TransportAvailability(true, true, true, true), TransportAvailability.of(ready.source()));
        assertEquals(RowPlayback.PLAYING, RowPlayback.of(true, ready.playing(), ready.isLoading()));
    }

    @Test
    void onceTheRequestIsGoneTheNormalStateIsShownAgain() {
        NowPlaying shown = NowPlayingTracker.decide(playlistPlaying(Optional.empty()), "mine");

        assertEquals("Suburban Tram", shown.title());
        assertEquals(LoadState.READY, shown.state());
        assertTrue(shown.playing());
    }

    @Test
    void aLoadingSnapshotCanNeverClaimToBePlaying() {
        NowPlaying forced = new NowPlaying(NowPlayingSource.PREVIEW, "", "x", "", ANGEL_FILE, true,
                LoadState.LOADING);

        assertFalse(forced.playing());
    }
}
