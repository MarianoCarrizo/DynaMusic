package com.marianocarrizo.dynamusic.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NowPlayingModelTest {

    private NowPlayingModel model;

    @BeforeEach
    void setUp() {
        model = new NowPlayingModel();
    }

    @Test
    void startsWithNothingPlaying() {
        assertSame(NowPlaying.NONE, model.get());
        assertEquals(NowPlayingSource.NONE, model.get().source());
        assertFalse(model.get().playing());
    }

    @Test
    void moodSourceKeepsItsName() {
        model.show(new NowPlaying(NowPlayingSource.MOOD, "RAGED", "Battle", "Album", true));

        assertEquals("RAGED", model.get().sourceName());
        assertEquals("Mood · Raged", model.get().badge());
        assertTrue(model.get().playing());
    }

    @Test
    void unnamedSourcesDropTheName() {
        NowPlaying onDemand = new NowPlaying(NowPlayingSource.ON_DEMAND, "ignored", "Song", "", true);
        NowPlaying preview = new NowPlaying(NowPlayingSource.PREVIEW, "ignored", "Song", "", true);

        assertEquals("", onDemand.sourceName());
        assertEquals("Playing now", onDemand.badge());
        assertEquals("", preview.sourceName());
        assertEquals("Preview", preview.badge());
    }

    @Test
    void nullNameAndTextBecomeEmpty() {
        NowPlaying snapshot = new NowPlaying(NowPlayingSource.PLAYLIST, null, null, null, false);

        assertEquals("", snapshot.sourceName());
        assertEquals("", snapshot.title());
        assertEquals("", snapshot.detail());
        assertEquals("Playlist", snapshot.badge());
    }

    @Test
    void noneSourceIsNeverPlaying() {
        NowPlaying snapshot = new NowPlaying(NowPlayingSource.NONE, "RAGED", "x", "", true);

        assertEquals("", snapshot.sourceName());
        assertFalse(snapshot.playing());
    }

    @Test
    void isSoundingNeedsBothTheSourceAndPlaying() {
        NowPlaying paused = new NowPlaying(NowPlayingSource.MOOD, "RAGED", "x", "", false);
        NowPlaying playing = new NowPlaying(NowPlayingSource.MOOD, "RAGED", "x", "", true);

        assertFalse(paused.isSounding(NowPlayingSource.MOOD));
        assertFalse(playing.isSounding(NowPlayingSource.PLAYLIST));
        assertTrue(playing.isSounding(NowPlayingSource.MOOD));
    }

    @Test
    void notifiesOncePerDistinctSnapshot() {
        List<NowPlaying> seen = new ArrayList<>();
        model.snapshotProperty().addListener((obs, oldValue, newValue) -> seen.add(newValue));
        NowPlaying first = new NowPlaying(NowPlayingSource.MOOD, "NORMAL", "A", "", true);

        model.show(first);
        model.show(new NowPlaying(NowPlayingSource.MOOD, "NORMAL", "A", "", true));
        model.show(new NowPlaying(NowPlayingSource.MOOD, "NORMAL", "A", "", false));

        assertEquals(2, seen.size());
        assertSame(first, seen.get(0));
        assertFalse(seen.get(1).playing());
    }

    @Test
    void onlyMoodAndPlaylistAreNamed() {
        for (NowPlayingSource source : NowPlayingSource.values()) {
            boolean expected = source == NowPlayingSource.MOOD || source == NowPlayingSource.PLAYLIST;
            assertEquals(expected, source.isNamed(), source.name());
        }
    }
}
