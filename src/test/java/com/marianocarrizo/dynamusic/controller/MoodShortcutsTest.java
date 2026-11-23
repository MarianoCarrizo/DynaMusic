package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoodShortcutsTest {

    private static final Object MOODS = new Object();
    private static final Object PLAYLISTS = new Object();

    @Test
    void moodKeysWorkOnTheMoodsTab() {
        assertTrue(MoodShortcuts.enabled(MOODS, MOODS));
    }

    @Test
    void moodKeysDoNothingOnAnyOtherTab() {
        assertFalse(MoodShortcuts.enabled(PLAYLISTS, MOODS));
    }

    @Test
    void moodKeysDoNothingWhenNoTabIsSelected() {
        assertFalse(MoodShortcuts.enabled(null, MOODS));
    }

    @Test
    void moodKeysDoNothingWithoutAMoodsTab() {
        assertFalse(MoodShortcuts.enabled(null, null));
    }
}
