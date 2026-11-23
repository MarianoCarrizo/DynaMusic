package com.marianocarrizo.dynamusic.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FillSpanTest {

    @Test
    void horizontalFillRunsFromTrackStartToThumbCenter() {
        FillSpan span = FillSpan.horizontal(10, 100, 60);

        assertEquals(10, span.start());
        assertEquals(50, span.length());
    }

    @Test
    void horizontalFillIsEmptyWhenThumbIsBeforeTheTrack() {
        assertEquals(0, FillSpan.horizontal(10, 100, 4).length());
    }

    @Test
    void horizontalFillNeverExceedsTheTrack() {
        assertEquals(100, FillSpan.horizontal(10, 100, 500).length());
    }

    @Test
    void verticalFillRunsFromThumbCenterToTrackBottom() {
        FillSpan span = FillSpan.vertical(0, 80, 30);

        assertEquals(30, span.start());
        assertEquals(50, span.length());
    }

    @Test
    void verticalFillIsClampedToTheTrack() {
        assertEquals(0, FillSpan.vertical(0, 80, 200).length());
        assertEquals(80, FillSpan.vertical(0, 80, -5).length());
    }
}
