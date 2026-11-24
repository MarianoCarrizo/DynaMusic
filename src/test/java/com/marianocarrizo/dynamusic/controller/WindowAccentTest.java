package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.AppMode;
import com.marianocarrizo.dynamusic.service.MusicService.PlaybackMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WindowAccentTest {

    @Test
    void moodModeUsesTheMoodId() {
        assertEquals("window-raged", WindowAccent.rootId(AppMode.SESSION, PlaybackMode.MOOD, TrackType.RAGED));
        assertEquals("window-normal", WindowAccent.rootId(AppMode.SESSION, PlaybackMode.MOOD, TrackType.NORMAL));
    }

    @Test
    void noActiveMoodKeepsTheBrandAccent() {
        assertNull(WindowAccent.rootId(AppMode.SESSION, PlaybackMode.MOOD, null));
    }

    @Test
    void playlistAndOnDemandKeepTheBrandAccent() {
        assertNull(WindowAccent.rootId(AppMode.SESSION, PlaybackMode.PLAYLIST, TrackType.RAGED));
        assertNull(WindowAccent.rootId(AppMode.SESSION, PlaybackMode.ON_DEMAND, TrackType.NORMAL));
    }

    @Test
    void listenModeAlwaysUsesTheBrandAccent() {
        assertNull(WindowAccent.rootId(AppMode.LISTEN, PlaybackMode.MOOD, TrackType.RAGED));
    }
}
