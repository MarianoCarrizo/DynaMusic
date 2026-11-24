package com.marianocarrizo.dynamusic.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SeekGestureTest {

    private static final double TOTAL = 200.0;
    private static final long NOW = 10_000;

    private final SeekGesture gesture = new SeekGesture();

    @Test
    void pressingDoesNotSeek() {
        gesture.press(0.4);

        assertTrue(gesture.isDragging());
        assertEquals(0.4, gesture.target(), 1e-9);
    }

    @Test
    void draggingMovesTheTargetWithoutSeeking() {
        gesture.press(0.1);
        gesture.drag(0.3);
        gesture.drag(0.7);

        assertEquals(0.7, gesture.target(), 1e-9);
        assertTrue(gesture.isDragging());
    }

    @Test
    void releasingSeeksOnceToTheReleasePoint() {
        gesture.press(0.1);
        gesture.drag(0.5);

        OptionalDouble seek = gesture.release(0.6, NOW);

        assertEquals(0.6, seek.getAsDouble(), 1e-9);
        assertFalse(gesture.isDragging());
        assertTrue(gesture.release(0.6, NOW).isEmpty());
    }

    @Test
    void aClickIsAPressAndReleaseAtTheSamePoint() {
        gesture.press(0.25);

        assertEquals(0.25, gesture.release(0.25, NOW).getAsDouble(), 1e-9);
    }

    @Test
    void draggingWithoutAPressIsIgnored() {
        gesture.drag(0.9);

        assertFalse(gesture.isDragging());
    }

    @Test
    void playbackPositionsAreIgnoredWhileDragging() {
        gesture.press(0.5);

        assertFalse(gesture.accepts(10, TOTAL, NOW));
    }

    @Test
    void stalePositionsAreIgnoredUntilTheSeekLands() {
        gesture.press(0.5);
        gesture.release(0.5, NOW);

        assertFalse(gesture.accepts(10, TOTAL, NOW + 20));
        assertFalse(gesture.accepts(40, TOTAL, NOW + 40));
        assertTrue(gesture.accepts(100.2, TOTAL, NOW + 60));
        assertTrue(gesture.accepts(10, TOTAL, NOW + 80));
    }

    @Test
    void positionsAreAcceptedAgainAfterTheLandingTimeout() {
        gesture.press(0.5);
        gesture.release(0.5, NOW);

        assertFalse(gesture.accepts(10, TOTAL, NOW + SeekGesture.LANDING_TIMEOUT_MILLIS - 1));
        assertTrue(gesture.accepts(10, TOTAL, NOW + SeekGesture.LANDING_TIMEOUT_MILLIS));
    }

    @Test
    void aNewPressSupersedesAPendingLanding() {
        gesture.press(0.5);
        gesture.release(0.5, NOW);
        gesture.press(0.8);
        gesture.release(0.8, NOW + 30);

        assertFalse(gesture.accepts(100, TOTAL, NOW + 40));
        assertTrue(gesture.accepts(160, TOTAL, NOW + 50));
    }

    @Test
    void jumpingSeeksImmediatelyAndGuardsAgainstStalePositions() {
        assertEquals(0.3, gesture.jump(0.3, NOW).getAsDouble(), 1e-9);

        assertFalse(gesture.accepts(5, TOTAL, NOW + 10));
        assertTrue(gesture.accepts(60, TOTAL, NOW + 20));
    }

    @Test
    void cancellingClearsDraggingAndLanding() {
        gesture.press(0.5);
        gesture.cancel();

        assertFalse(gesture.isDragging());
        assertTrue(gesture.accepts(1, TOTAL, NOW));
    }

    @Test
    void aClickMapsToTheExactPointOnTheTrack() {
        assertEquals(0.5, SeekGesture.fractionAt(60, 10, 100), 1e-9);
        assertEquals(0.25, SeekGesture.fractionAt(35, 10, 100), 1e-9);
    }

    @Test
    void positionsOutsideTheTrackClampToItsEnds() {
        assertEquals(0.0, SeekGesture.fractionAt(2, 10, 100), 1e-9);
        assertEquals(1.0, SeekGesture.fractionAt(500, 10, 100), 1e-9);
    }

    @Test
    void anEmptyTrackMapsToTheStart() {
        assertEquals(0.0, SeekGesture.fractionAt(50, 10, 0), 1e-9);
    }
}
