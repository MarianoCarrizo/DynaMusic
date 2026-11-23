package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.service.MusicService.PlaybackMode;
import com.marianocarrizo.dynamusic.service.PlaybackState;
import com.marianocarrizo.dynamusic.service.SoundingTrack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NowPlayingTrackerTest {

    private static final SoundingTrack PLAYING_TRACK = new SoundingTrack("04 Opening Stage", "Mega Man X", true);
    private static final SoundingTrack PAUSED_TRACK = new SoundingTrack("Boss Theme", "", false);

    private static PlaybackState state(PlaybackMode mode, boolean previewSoundedLast, TrackType moodType,
                                       SoundingTrack preview, SoundingTrack mood, SoundingTrack playlist,
                                       SoundingTrack onDemand) {
        return new PlaybackState(mode, previewSoundedLast, moodType, preview, mood, playlist, onDemand);
    }

    private static PlaybackState moodState(SoundingTrack mood) {
        return state(PlaybackMode.MOOD, false, TrackType.RAGED, SoundingTrack.ABSENT, mood,
                SoundingTrack.ABSENT, SoundingTrack.ABSENT);
    }

    @Test
    void aPreviewThatSoundedLastWinsOverEveryMode() {
        PlaybackState state = state(PlaybackMode.MOOD, true, TrackType.RAGED, PLAYING_TRACK,
                new SoundingTrack("Mood track", "", true), SoundingTrack.ABSENT, SoundingTrack.ABSENT);

        NowPlaying result = NowPlayingTracker.decide(state, null);

        assertEquals(NowPlayingSource.PREVIEW, result.source());
        assertEquals("04 Opening Stage", result.title());
        assertEquals("Mega Man X", result.detail());
        assertTrue(result.playing());
    }

    @Test
    void moodModeNamesTheMoodAndKeepsThePlayingFlag() {
        NowPlaying result = NowPlayingTracker.decide(moodState(PAUSED_TRACK), null);

        assertEquals(NowPlayingSource.MOOD, result.source());
        assertEquals("RAGED", result.sourceName());
        assertEquals("MOOD · RAGED", result.badge());
        assertFalse(result.playing());
    }

    @Test
    void moodModeWithoutALoadedTrackIsNothing() {
        assertSame(NowPlaying.NONE, NowPlayingTracker.decide(moodState(SoundingTrack.ABSENT), null));
    }

    @Test
    void moodModeWithoutAMoodTypeIsUnnamed() {
        PlaybackState state = state(PlaybackMode.MOOD, false, null, SoundingTrack.ABSENT, PAUSED_TRACK,
                SoundingTrack.ABSENT, SoundingTrack.ABSENT);

        assertEquals("MOOD", NowPlayingTracker.decide(state, null).badge());
    }

    @Test
    void playlistModeUsesThePlaylistNameAndTrack() {
        PlaybackState state = state(PlaybackMode.PLAYLIST, false, TrackType.NORMAL, SoundingTrack.ABSENT,
                new SoundingTrack("Mood track", "", true), PLAYING_TRACK, SoundingTrack.ABSENT);

        NowPlaying result = NowPlayingTracker.decide(state, "megaman");

        assertEquals("PLAYLIST · megaman", result.badge());
        assertEquals("04 Opening Stage", result.title());
        assertTrue(result.isSounding(NowPlayingSource.PLAYLIST));
    }

    @Test
    void playlistModeWithoutATrackIsNothing() {
        PlaybackState state = state(PlaybackMode.PLAYLIST, false, TrackType.NORMAL, SoundingTrack.ABSENT,
                SoundingTrack.ABSENT, SoundingTrack.ABSENT, SoundingTrack.ABSENT);

        assertSame(NowPlaying.NONE, NowPlayingTracker.decide(state, "megaman"));
    }

    @Test
    void onDemandModeIsUnnamed() {
        PlaybackState state = state(PlaybackMode.ON_DEMAND, false, TrackType.NORMAL, SoundingTrack.ABSENT,
                SoundingTrack.ABSENT, SoundingTrack.ABSENT, PAUSED_TRACK);

        NowPlaying result = NowPlayingTracker.decide(state, "ignored");

        assertEquals("PLAYING NOW", result.badge());
        assertEquals("", result.sourceName());
    }

    @Test
    void syncPublishesTheDecisionToTheModel() {
        NowPlayingModel model = new NowPlayingModel();
        NowPlayingTracker tracker = new NowPlayingTracker(() -> moodState(PLAYING_TRACK), () -> null, model);

        tracker.sync();

        assertEquals("04 Opening Stage", model.get().title());
        assertTrue(model.get().playing());
    }

    @Test
    void syncFollowsTheLatestStateOnEveryCall() {
        NowPlayingModel model = new NowPlayingModel();
        PlaybackState[] current = {moodState(PLAYING_TRACK)};
        NowPlayingTracker tracker = new NowPlayingTracker(() -> current[0], () -> null, model);
        tracker.sync();

        current[0] = moodState(SoundingTrack.ABSENT);
        tracker.sync();

        assertSame(NowPlaying.NONE, model.get());
    }
}
