package com.marianocarrizo.dynamusic.model;

import javafx.beans.property.SimpleStringProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NowPlayingModelTest {

    private NowPlayingModel model;

    @BeforeEach
    void setUp() {
        model = new NowPlayingModel();
    }

    @Test
    void startsWithNothingPlaying() {
        assertEquals(NowPlayingSource.NONE, model.getSource());
        assertEquals("", model.getSourceName());
        assertFalse(model.isPlaying());
        assertEquals(NowPlayingModel.NO_TITLE, model.titleProperty().get());
    }

    @Test
    void moodSourceKeepsItsName() {
        model.show(NowPlayingSource.MOOD, "RAGED", true);

        assertEquals(NowPlayingSource.MOOD, model.getSource());
        assertEquals("RAGED", model.getSourceName());
        assertTrue(model.isPlaying());
    }

    @Test
    void playlistSourceKeepsItsName() {
        model.show(NowPlayingSource.PLAYLIST, "Night drive", false);

        assertEquals("Night drive", model.getSourceName());
        assertFalse(model.isPlaying());
    }

    @Test
    void unnamedSourcesDropTheName() {
        model.show(NowPlayingSource.ON_DEMAND, "ignored", true);
        assertEquals("", model.getSourceName());

        model.show(NowPlayingSource.PREVIEW, "ignored", true);
        assertEquals("", model.getSourceName());
    }

    @Test
    void nullNameBecomesEmpty() {
        model.show(NowPlayingSource.PLAYLIST, null, true);

        assertEquals("", model.getSourceName());
    }

    @Test
    void noneSourceIsNeverPlaying() {
        model.show(NowPlayingSource.NONE, "RAGED", true);

        assertEquals("", model.getSourceName());
        assertFalse(model.isPlaying());
    }

    @Test
    void notifiesWhenTheSourceChanges() {
        List<NowPlayingSource> seen = new ArrayList<>();
        model.sourceProperty().addListener((obs, oldValue, newValue) -> seen.add(newValue));

        model.show(NowPlayingSource.MOOD, "NORMAL", true);
        model.show(NowPlayingSource.MOOD, "NORMAL", false);
        model.show(NowPlayingSource.PREVIEW, "", true);

        assertEquals(List.of(NowPlayingSource.MOOD, NowPlayingSource.PREVIEW), seen);
    }

    @Test
    void titleCanFollowAnotherProperty() {
        SimpleStringProperty origin = new SimpleStringProperty("First track");
        model.titleProperty().bind(origin);

        origin.set("Second track");

        assertEquals("Second track", model.titleProperty().get());
    }

    @Test
    void onlyMoodAndPlaylistAreNamed() {
        for (NowPlayingSource source : NowPlayingSource.values()) {
            boolean expected = source == NowPlayingSource.MOOD || source == NowPlayingSource.PLAYLIST;
            assertEquals(expected, source.isNamed(), source.name());
        }
    }
}
