package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.service.MusicService.PlaybackMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WindowAccentTest {

    @Test
    void moodModeUsesTheMoodId() {
        assertEquals("window-raged", WindowAccent.rootId(PlaybackMode.MOOD, TrackType.RAGED));
        assertEquals("window-normal", WindowAccent.rootId(PlaybackMode.MOOD, TrackType.NORMAL));
    }

    @Test
    void noActiveMoodKeepsTheBrandAccent() {
        assertNull(WindowAccent.rootId(PlaybackMode.MOOD, null));
    }

    @Test
    void playlistAndOnDemandKeepTheBrandAccent() {
        assertNull(WindowAccent.rootId(PlaybackMode.PLAYLIST, TrackType.RAGED));
        assertNull(WindowAccent.rootId(PlaybackMode.ON_DEMAND, TrackType.NORMAL));
    }
}
