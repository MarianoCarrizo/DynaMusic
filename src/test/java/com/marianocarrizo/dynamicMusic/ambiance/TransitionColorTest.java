package com.marianocarrizo.dynamicMusic.ambiance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionColorTest {

    private static final String PRIMARY = "#FF5A1F";
    private static final String SECONDARY = "#0B0614";

    @Test
    void fireTintsWithThePrimaryColor() {
        assertEquals(0xFF5A1F, TransitionStyle.FIRE.overlayRgb(PRIMARY, SECONDARY));
    }

    @Test
    void shadowVignetteUsesTheSecondaryColor() {
        assertEquals(0x0B0614, TransitionStyle.SHADOW.overlayRgb(PRIMARY, SECONDARY));
    }

    @Test
    void electricAndHolyLightenThePrimaryTowardWhite() {
        for (TransitionStyle style : new TransitionStyle[] {TransitionStyle.ELECTRIC, TransitionStyle.HOLY}) {
            int rgb = style.overlayRgb("#000000", SECONDARY);
            int channel = rgb & 0xFF;
            assertEquals(channel, (rgb >> 8) & 0xFF, style.name());
            assertEquals(channel, (rgb >> 16) & 0xFF, style.name());
            assertEquals(Math.round(255 * 0.6), channel, style.name());
        }
    }

    @Test
    void lighteningKeepsWhiteWhiteAndBrightensColors() {
        assertEquals(0xFFFFFF, TransitionStyle.HOLY.overlayRgb("#FFFFFF", SECONDARY));
        int lightened = TransitionStyle.ELECTRIC.overlayRgb("#4FC3FF", SECONDARY);
        assertNotEquals(0x4FC3FF, lightened);
        assertTrue(((lightened >> 16) & 0xFF) > 0x4F);
        assertEquals(0xFF, lightened & 0xFF);
    }

    @Test
    void hexWithoutHashIsAccepted() {
        assertEquals(0xFF5A1F, TransitionStyle.FIRE.overlayRgb("FF5A1F", SECONDARY));
        assertEquals(0x0B0614, TransitionStyle.SHADOW.overlayRgb(PRIMARY, "0b0614"));
    }

    @Test
    void nullOrInvalidHexFallsBackToTheStyleDefault() {
        assertEquals(0xFF7A1A, TransitionStyle.FIRE.overlayRgb(null, SECONDARY));
        assertEquals(0xFF7A1A, TransitionStyle.FIRE.overlayRgb("not-a-color", SECONDARY));
        assertEquals(0xFF7A1A, TransitionStyle.FIRE.overlayRgb("#FFF", SECONDARY));
        assertEquals(0xCFF6FF, TransitionStyle.ELECTRIC.overlayRgb("#GGGGGG", SECONDARY));
        assertEquals(0xFFF8E0, TransitionStyle.HOLY.overlayRgb(null, SECONDARY));
        assertEquals(0x000000, TransitionStyle.SHADOW.overlayRgb(PRIMARY, null));
    }

    @Test
    void neutralHasNoColorSource() {
        assertEquals(TransitionStyle.ColorSource.NONE, TransitionStyle.NEUTRAL.colorSource());
    }
}
