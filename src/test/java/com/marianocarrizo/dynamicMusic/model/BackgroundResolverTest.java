package com.marianocarrizo.dynamicMusic.model;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundResolverTest {

    private static final Predicate<String> EXISTS = path -> !path.contains("missing");

    private static BackgroundConfig config(String path) {
        BackgroundConfig c = new BackgroundConfig();
        c.setPath(path);
        return c;
    }

    private static BackgroundConfig resolve(Scene scene, AuraSlot slot) {
        return BackgroundResolver.resolve(scene, slot, EXISTS, true);
    }

    private static Scene sceneWithDefault() {
        Scene scene = new Scene("S");
        scene.getBackground().setPath("C:/default.png");
        return scene;
    }

    @Test
    void firstApplyAlwaysShows() {
        assertTrue(BackgroundResolver.needsShow(null, resolve(sceneWithDefault(), AuraSlot.NORMAL)));
    }

    @Test
    void identicalConfigsDoNotShowAgain() {
        Scene scene = sceneWithDefault();
        BackgroundConfig applied = resolve(scene, AuraSlot.NORMAL);

        assertFalse(BackgroundResolver.needsShow(applied, resolve(scene, AuraSlot.RAGED)));
    }

    @Test
    void editingTheDefaultWhileAnOverrideIsLiveDoesNotShowAgain() {
        Scene scene = sceneWithDefault();
        scene.setBackground(AuraSlot.RAGED, config("C:/raged.png"));
        BackgroundConfig applied = resolve(scene, AuraSlot.RAGED);

        scene.getBackground().setPath("C:/other-default.png");

        assertFalse(BackgroundResolver.needsShow(applied, resolve(scene, AuraSlot.RAGED)));
    }

    @Test
    void editingAnOverrideOfAnotherSlotDoesNotShowAgain() {
        Scene scene = sceneWithDefault();
        BackgroundConfig applied = resolve(scene, AuraSlot.NORMAL);

        scene.setBackground(AuraSlot.PAUSED, config("C:/paused.png"));

        assertFalse(BackgroundResolver.needsShow(applied, resolve(scene, AuraSlot.NORMAL)));
    }

    @Test
    void removingTheLiveOverrideShowsTheDefault() {
        Scene scene = sceneWithDefault();
        scene.setBackground(AuraSlot.RAGED, config("C:/raged.png"));
        BackgroundConfig applied = resolve(scene, AuraSlot.RAGED);

        scene.clearBackground(AuraSlot.RAGED);
        BackgroundConfig next = resolve(scene, AuraSlot.RAGED);

        assertTrue(BackgroundResolver.needsShow(applied, next));
        assertEquals("C:/default.png", next.getPath());
    }

    @Test
    void overrideWithMissingFileFallsBackButIsKept() {
        Scene scene = sceneWithDefault();
        scene.setBackground(AuraSlot.RAGED, config("C:/missing.png"));

        BackgroundConfig shown = resolve(scene, AuraSlot.RAGED);

        assertEquals("C:/default.png", shown.getPath());
        assertTrue(scene.hasOwnBackground(AuraSlot.RAGED));
        assertEquals("C:/missing.png", scene.backgroundFor(AuraSlot.RAGED).getPath());
    }

    @Test
    void missingDefaultShowsNothingWithoutTouchingTheScene() {
        Scene scene = new Scene("S");
        scene.getBackground().setPath("C:/missing.png");

        assertNull(resolve(scene, AuraSlot.NORMAL).getPath());
        assertEquals("C:/missing.png", scene.getBackground().getPath());
    }

    @Test
    void unsetRateAndExplicitDefaultRateAreTheSame() {
        Scene scene = sceneWithDefault();
        BackgroundConfig applied = resolve(scene, AuraSlot.NORMAL);

        scene.getBackground().setPlaybackRate(1.0);

        assertFalse(BackgroundResolver.needsShow(applied, resolve(scene, AuraSlot.NORMAL)));
    }

    @Test
    void unavailableBoomerangIsDisabledForDisplayOnly() {
        Scene scene = sceneWithDefault();
        scene.getBackground().setSegmentStartMs(1000L);
        scene.getBackground().setSegmentEndMs(5000L);
        scene.getBackground().setBoomerangEnabled(true);

        BackgroundConfig shown = BackgroundResolver.resolve(scene, AuraSlot.NORMAL, EXISTS, false);

        assertFalse(Boolean.TRUE.equals(shown.getBoomerangEnabled()));
        assertEquals(Boolean.TRUE, scene.getBackground().getBoomerangEnabled());
        assertEquals(1000L, scene.getBackground().getSegmentStartMs());
    }
}
