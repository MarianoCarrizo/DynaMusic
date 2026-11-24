package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoodShortcutsTest {

    private static final Object MOODS = new Object();
    private static final Object PLAYLISTS = new Object();

    @Test
    void moodKeysWorkOnTheMoodsTab() {
        assertTrue(MoodShortcuts.enabled(AppMode.SESSION, MOODS, MOODS));
    }

    @Test
    void moodKeysDoNothingOnAnyOtherTab() {
        assertFalse(MoodShortcuts.enabled(AppMode.SESSION, PLAYLISTS, MOODS));
    }

    @Test
    void moodKeysDoNothingWhenNoTabIsSelected() {
        assertFalse(MoodShortcuts.enabled(AppMode.SESSION, null, MOODS));
    }

    @Test
    void moodKeysDoNothingWithoutAMoodsTab() {
        assertFalse(MoodShortcuts.enabled(AppMode.SESSION, null, null));
    }

    @Test
    void moodKeysDoNothingInListenMode() {
        assertFalse(MoodShortcuts.enabled(AppMode.LISTEN, MOODS, MOODS));
    }
}
