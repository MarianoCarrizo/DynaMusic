package com.marianocarrizo.dynamusic.model;

import java.util.function.Predicate;

public final class BackgroundResolver {

    private BackgroundResolver() {}

    public static BackgroundConfig resolve(Scene scene, AuraSlot slot,
                                           Predicate<String> usableMedia, boolean boomerangAvailable) {
        BackgroundConfig display = pickSource(scene, slot, usableMedia).copy();
        if (display.getPath() != null && !usableMedia.test(display.getPath())) {
            display.setPath(null);
            display.setSegmentStartMs(null);
            display.setSegmentEndMs(null);
        }
        boolean hasSegment = display.getSegmentStartMs() != null && display.getSegmentEndMs() != null;
        if (!boomerangAvailable || !hasSegment) display.setBoomerangEnabled(false);
        return display;
    }

    public static boolean needsShow(BackgroundConfig applied, BackgroundConfig next) {
        return applied == null || !applied.sameLook(next);
    }

    private static BackgroundConfig pickSource(Scene scene, AuraSlot slot, Predicate<String> usableMedia) {
        BackgroundConfig effective = scene.backgroundFor(slot);
        boolean overrideUsable = scene.hasOwnBackground(slot) && usableMedia.test(effective.getPath());
        return overrideUsable ? effective : scene.getBackground();
    }
}
