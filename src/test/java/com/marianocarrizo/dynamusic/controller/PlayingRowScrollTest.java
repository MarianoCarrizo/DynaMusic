package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayingRowScrollTest {

    private static final RowRange VISIBLE = new RowRange(10, 19);

    @Test
    void scrollsWhenTheNewTrackIsBelowTheVisibleRows() {
        assertTrue(PlayingRowScroll.shouldScroll("a", "b", 25, VISIBLE));
    }

    @Test
    void scrollsWhenTheNewTrackIsAboveTheVisibleRows() {
        assertTrue(PlayingRowScroll.shouldScroll("a", "b", 2, VISIBLE));
    }

    @Test
    void doesNotScrollWhenTheNewTrackIsAlreadyVisible() {
        assertFalse(PlayingRowScroll.shouldScroll("a", "b", 10, VISIBLE));
        assertFalse(PlayingRowScroll.shouldScroll("a", "b", 19, VISIBLE));
    }

    @Test
    void doesNotScrollWhenThePlayingTrackDidNotChange() {
        assertFalse(PlayingRowScroll.shouldScroll("a", "a", 40, VISIBLE));
    }

    @Test
    void doesNotScrollWhenNothingIsPlayingOrTheIndexIsUnknown() {
        assertFalse(PlayingRowScroll.shouldScroll("a", null, 40, VISIBLE));
        assertFalse(PlayingRowScroll.shouldScroll("a", "b", -1, VISIBLE));
    }

    @Test
    void scrollsWhenNothingIsMeasuredYetAndTheTrackChanged() {
        assertTrue(PlayingRowScroll.shouldScroll(null, "b", 4, RowRange.EMPTY));
    }

    @Test
    void centersTheRowUsingTheVisibleRowCount() {
        assertEquals(35, PlayingRowScroll.topIndexFor(40, VISIBLE));
    }

    @Test
    void neverScrollsAboveTheFirstRow() {
        assertEquals(0, PlayingRowScroll.topIndexFor(1, VISIBLE));
        assertEquals(0, PlayingRowScroll.topIndexFor(1, RowRange.EMPTY));
    }

    @Test
    void usesAFixedOffsetWhenNothingIsMeasured() {
        assertEquals(7, PlayingRowScroll.topIndexFor(10, RowRange.EMPTY));
    }
}
