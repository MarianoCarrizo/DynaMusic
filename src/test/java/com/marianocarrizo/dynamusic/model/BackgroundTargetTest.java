package com.marianocarrizo.dynamusic.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundTargetTest {

    @Test
    void everySlotHasExactlyOneTargetAfterTheSceneDefault() {
        assertNull(BackgroundTarget.SCENE_DEFAULT.slot());
        assertEquals(AuraSlot.values().length + 1, BackgroundTarget.values().length);
        for (AuraSlot slot : AuraSlot.values()) {
            assertEquals(slot, BackgroundTarget.valueOf(slot.name()).slot());
        }
    }

    @Test
    void ofMapsEachSlotToItsTargetAndNullToTheSceneDefault() {
        for (AuraSlot slot : AuraSlot.values()) {
            assertEquals(slot, BackgroundTarget.of(slot).slot());
        }
        assertEquals(BackgroundTarget.SCENE_DEFAULT, BackgroundTarget.of(null));
    }

    @Test
    void sceneDefaultTargetEditsTheSceneBackground() {
        Scene scene = new Scene("S");
        assertSame(scene.getBackground(), BackgroundTarget.SCENE_DEFAULT.configIn(scene));
        assertSame(scene.getBackground(), BackgroundTarget.SCENE_DEFAULT.getOrCreateIn(scene));
    }

    @Test
    void slotTargetHasNoConfigUntilItIsMadeEditable() {
        Scene scene = new Scene("S");
        scene.getBackground().setFitMode("FIT");

        assertNull(BackgroundTarget.RAGED.configIn(scene));

        BackgroundConfig created = BackgroundTarget.RAGED.getOrCreateIn(scene);
        created.setPath("C:/raged.png");

        assertEquals("FIT", created.getFitMode());
        assertSame(created, BackgroundTarget.RAGED.configIn(scene));
        assertSame(created, scene.backgroundFor(AuraSlot.RAGED));
    }

    @Test
    void getOrCreateReplacesAnOverrideWithABlankPath() {
        Scene scene = new Scene("S");
        BackgroundConfig blank = new BackgroundConfig();
        blank.setPath(" ");
        scene.setBackground(AuraSlot.NORMAL, blank);

        BackgroundConfig created = BackgroundTarget.NORMAL.getOrCreateIn(scene);

        assertNotSame(blank, created);
        created.setPath("C:/normal.png");
        assertSame(created, scene.backgroundFor(AuraSlot.NORMAL));
    }

    @Test
    void clearingASlotFallsBackToTheSceneDefault() {
        Scene scene = new Scene("S");
        scene.getBackground().setPath("C:/default.png");
        BackgroundTarget.PAUSED.getOrCreateIn(scene).setPath("C:/paused.png");

        BackgroundTarget.PAUSED.clearIn(scene);

        assertFalse(scene.hasOwnBackground(AuraSlot.PAUSED));
        assertEquals("C:/default.png", scene.backgroundFor(AuraSlot.PAUSED).getPath());
    }

    @Test
    void clearingTheSceneDefaultKeepsItsFitModeAndLeavesOverrides() {
        Scene scene = new Scene("S");
        scene.getBackground().setPath("C:/default.png");
        scene.getBackground().setFitMode("STRETCH");
        BackgroundTarget.NORMAL.getOrCreateIn(scene).setPath("C:/normal.png");

        BackgroundTarget.SCENE_DEFAULT.clearIn(scene);

        assertNull(scene.getBackground().getPath());
        assertEquals("STRETCH", scene.getBackground().getFitMode());
        assertTrue(scene.hasOwnBackground(AuraSlot.NORMAL));
    }
}
