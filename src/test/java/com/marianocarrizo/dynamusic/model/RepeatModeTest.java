package com.marianocarrizo.dynamusic.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatModeTest {

    @Test
    void storedNamesAreParsedIgnoringCase() {
        assertEquals(RepeatMode.ONE, RepeatMode.fromStored("one"));
        assertEquals(RepeatMode.ALL, RepeatMode.fromStored("ALL"));
    }

    @Test
    void legacyBooleansMapToAllAndOff() {
        assertEquals(RepeatMode.ALL, RepeatMode.fromStored("true"));
        assertEquals(RepeatMode.OFF, RepeatMode.fromStored("false"));
    }

    @Test
    void missingOrUnknownValuesMapToOff() {
        assertEquals(RepeatMode.OFF, RepeatMode.fromStored(null));
        assertEquals(RepeatMode.OFF, RepeatMode.fromStored(""));
        assertEquals(RepeatMode.OFF, RepeatMode.fromStored("sideways"));
    }

    @Test
    void onlyOneRepeatsTheTrack() {
        assertFalse(RepeatMode.OFF.repeatsTrack());
        assertFalse(RepeatMode.ALL.repeatsTrack());
        assertTrue(RepeatMode.ONE.repeatsTrack());
    }

    @Test
    void everyModeHasADistinctHint() {
        assertEquals("Repeat", RepeatMode.OFF.hint());
        assertEquals("Repeat all", RepeatMode.ALL.hint());
        assertEquals("Repeat one", RepeatMode.ONE.hint());
    }

    @Test
    void playlistsAcceptAllModesAndOtherSourcesFoldAllIntoOne() {
        assertEquals(RepeatMode.ALL, NowPlayingSource.PLAYLIST.supportedRepeat(RepeatMode.ALL));
        assertEquals(RepeatMode.ONE, NowPlayingSource.ON_DEMAND.supportedRepeat(RepeatMode.ALL));
        assertEquals(RepeatMode.OFF, NowPlayingSource.NONE.supportedRepeat(RepeatMode.ONE));
    }
}
