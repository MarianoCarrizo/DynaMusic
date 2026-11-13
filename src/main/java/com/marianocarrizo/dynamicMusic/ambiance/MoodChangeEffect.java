package com.marianocarrizo.dynamicMusic.ambiance;

public enum MoodChangeEffect {
    NONE,
    OVERLAY_ONLY,
    FULL_TRANSITION;

    public static MoodChangeEffect decide(boolean moodChanged, boolean backgroundChanged) {
        if (!moodChanged) return NONE;
        return backgroundChanged ? FULL_TRANSITION : OVERLAY_ONLY;
    }
}
