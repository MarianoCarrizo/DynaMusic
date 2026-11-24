package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResampleTargetTest {

    @Test
    void the44100FamilyPrefers44100() {
        for (double rate : new double[] {44_100, 88_200, 176_400}) {
            assertEquals(List.of(ResampleTarget.HZ_44100, ResampleTarget.HZ_48000),
                    ResampleTarget.preferenceFor(rate));
        }
    }

    @Test
    void theOtherRatesPrefer48000() {
        for (double rate : new double[] {48_000, 96_000, 192_000, 32_000}) {
            assertEquals(List.of(ResampleTarget.HZ_48000, ResampleTarget.HZ_44100),
                    ResampleTarget.preferenceFor(rate));
        }
    }

    @Test
    void exposesTheTargetRate() {
        assertEquals(48_000, ResampleTarget.HZ_48000.rate());
    }
}
