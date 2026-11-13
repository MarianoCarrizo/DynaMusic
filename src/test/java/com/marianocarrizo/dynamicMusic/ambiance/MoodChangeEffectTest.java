package com.marianocarrizo.dynamicMusic.ambiance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoodChangeEffectTest {

    @Test
    void sameMoodNeverPlaysAnEffect() {
        assertEquals(MoodChangeEffect.NONE, MoodChangeEffect.decide(false, false));
        assertEquals(MoodChangeEffect.NONE, MoodChangeEffect.decide(false, true));
    }

    @Test
    void moodChangeWithANewBackgroundPlaysTheFullTransition() {
        assertEquals(MoodChangeEffect.FULL_TRANSITION, MoodChangeEffect.decide(true, true));
    }

    @Test
    void moodChangeOnTheSameBackgroundPlaysOnlyTheOverlay() {
        assertEquals(MoodChangeEffect.OVERLAY_ONLY, MoodChangeEffect.decide(true, false));
    }
}
