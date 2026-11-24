package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WasapiPeriodTest {

    @Test
    void theDefaultPeriodIsTwentyMilliseconds() {
        assertEquals(200_000, WasapiPeriod.DEFAULT_HNS);
    }

    @Test
    void theDeviceMinimumRaisesTheRequestedPeriod() {
        assertEquals(300_000, WasapiPeriod.target(200_000, 300_000));
        assertEquals(200_000, WasapiPeriod.target(200_000, 30_000));
    }

    @Test
    void alignedPeriodRoundsTheDurationOfWholeFrames() {
        assertEquals(200_000, WasapiPeriod.aligned(1_920, 96_000));
        assertEquals(200_000, WasapiPeriod.aligned(882, 44_100));
        assertEquals(200_000, WasapiPeriod.aligned(960, 48_000));
    }

    @Test
    void alignedPeriodFollowsTheFrameCountTheDeviceSuggests() {
        assertEquals(213_333, WasapiPeriod.aligned(1_024, 48_000));
    }
}
