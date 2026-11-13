package com.marianocarrizo.dynamicMusic.ambiance;

import com.marianocarrizo.dynamicMusic.model.AuraPreset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionStyleTest {

    @Test
    void everyPresetMapsToItsOwnStyle() {
        assertEquals(TransitionStyle.FIRE, TransitionStyle.forPreset(AuraPreset.FIRE));
        assertEquals(TransitionStyle.ELECTRIC, TransitionStyle.forPreset(AuraPreset.ELECTRIC));
        assertEquals(TransitionStyle.HOLY, TransitionStyle.forPreset(AuraPreset.HOLY));
        assertEquals(TransitionStyle.SHADOW, TransitionStyle.forPreset(AuraPreset.SHADOW));
        assertEquals(TransitionStyle.NEUTRAL, TransitionStyle.forPreset(AuraPreset.NEUTRAL));
        assertEquals(AuraPreset.values().length, TransitionStyle.values().length);
    }

    @Test
    void nullPresetFallsBackToNeutral() {
        assertEquals(TransitionStyle.NEUTRAL, TransitionStyle.forPreset(null));
    }

    @Test
    void everyCurveStaysWithinZeroAndOneAcrossTheTransition() {
        for (TransitionStyle style : TransitionStyle.values()) {
            for (int i = 0; i <= 1000; i++) {
                double p = i / 1000.0;
                assertInUnitRange(style.incomingOpacity(p), style, p);
                assertInUnitRange(style.overlayOpacity(p), style, p);
                assertInUnitRange(style.vignetteOpenness(p), style, p);
            }
        }
    }

    private static void assertInUnitRange(double value, TransitionStyle style, double progress) {
        assertTrue(value >= 0.0 && value <= 1.0, style + " out of range at " + progress + ": " + value);
    }

    @Test
    void everyStyleStartsOnTheOutgoingBackgroundAndEndsOnTheIncomingOne() {
        for (TransitionStyle style : TransitionStyle.values()) {
            assertEquals(0.0, style.incomingOpacity(0.0), 1e-9, style.name());
            assertEquals(1.0, style.incomingOpacity(1.0), 1e-9, style.name());
        }
    }

    @Test
    void solidOverlaysAreClearAtBothEnds() {
        for (TransitionStyle style : TransitionStyle.values()) {
            if (style.overlay() != TransitionStyle.Overlay.SOLID) continue;
            assertEquals(0.0, style.overlayOpacity(0.0), 1e-9, style.name());
            assertEquals(0.0, style.overlayOpacity(1.0), 1e-9, style.name());
        }
    }

    @Test
    void warmAndGlowOverlaysPeakWhileTheBackgroundIsSwapped() {
        for (TransitionStyle style : new TransitionStyle[] {TransitionStyle.FIRE, TransitionStyle.HOLY}) {
            assertTrue(style.overlayOpacity(0.45) > 0.5, style.name());
            assertTrue(style.incomingOpacity(0.1) < 0.1, style.name());
            assertTrue(style.incomingOpacity(0.9) > 0.9, style.name());
        }
    }

    @Test
    void electricFlashesThreeTimesThenCutsToTheNewBackground() {
        TransitionStyle style = TransitionStyle.ELECTRIC;
        int flashes = 0;
        boolean lit = false;
        for (int i = 0; i <= 1000; i++) {
            boolean nowLit = style.overlayOpacity(i / 1000.0) > 0.45;
            if (nowLit && !lit) flashes++;
            lit = nowLit;
        }
        assertEquals(3, flashes);
        assertEquals(0.0, style.incomingOpacity(0.5), 1e-9);
        assertEquals(1.0, style.incomingOpacity(0.8), 1e-9);
    }

    @Test
    void shadowVignetteClosesFullyBeforeCuttingAndOpensAgain() {
        TransitionStyle style = TransitionStyle.SHADOW;
        assertEquals(1.0, style.vignetteOpenness(0.0), 1e-9);
        assertEquals(0.0, style.vignetteOpenness(0.5), 1e-9);
        assertEquals(1.0, style.vignetteOpenness(1.0), 1e-9);
        assertEquals(0.0, style.incomingOpacity(0.49), 1e-9);
        assertEquals(1.0, style.incomingOpacity(0.5), 1e-9);
    }

    @Test
    void neutralIsAPlainCrossfade() {
        TransitionStyle style = TransitionStyle.NEUTRAL;
        assertEquals(0.25, style.incomingOpacity(0.25), 1e-9);
        assertEquals(0.0, style.overlayOpacity(0.5), 1e-9);
    }

    @Test
    void progressOutsideTheRangeIsClamped() {
        assertEquals(0.0, TransitionStyle.NEUTRAL.incomingOpacity(-1.0), 1e-9);
        assertEquals(1.0, TransitionStyle.NEUTRAL.incomingOpacity(2.0), 1e-9);
    }
}
