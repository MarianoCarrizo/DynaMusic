package com.marianocarrizo.dynamicMusic.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundConfigTest {

    private static BackgroundConfig full() {
        BackgroundConfig c = new BackgroundConfig();
        c.setPath("C:/bg.mp4");
        c.setFitMode("FIT");
        c.setSegmentStartMs(1000L);
        c.setSegmentEndMs(5000L);
        c.setPlaybackRate(1.5);
        c.setBoomerangEnabled(true);
        return c;
    }

    @Test
    void sameLookIsTrueForACopyAndForTwoEmptyConfigs() {
        assertTrue(full().sameLook(full().copy()));
        assertTrue(new BackgroundConfig().sameLook(new BackgroundConfig()));
    }

    @Test
    void sameLookIsFalseWhenAnyFieldDiffers() {
        BackgroundConfig base = full();
        BackgroundConfig path = full();
        path.setPath("C:/other.mp4");
        BackgroundConfig fit = full();
        fit.setFitMode("FILL");
        BackgroundConfig start = full();
        start.setSegmentStartMs(null);
        BackgroundConfig end = full();
        end.setSegmentEndMs(6000L);
        BackgroundConfig rate = full();
        rate.setPlaybackRate(1.0);
        BackgroundConfig boomerang = full();
        boomerang.setBoomerangEnabled(false);

        for (BackgroundConfig other : new BackgroundConfig[] {path, fit, start, end, rate, boomerang}) {
            assertFalse(base.sameLook(other));
        }
    }

    @Test
    void sameLookIsFalseForNull() {
        assertFalse(full().sameLook(null));
    }
}
