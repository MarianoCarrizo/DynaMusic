package com.marianocarrizo.dynamicMusic.model;

import com.google.gson.Gson;
import com.marianocarrizo.dynamicMusic.Enums.TrackType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneTest {

    @Test
    void getAuraLazilyFillsEverySlotWithItsDefaultPreset() {
        Scene scene = new Scene("S");

        assertEquals(AuraPreset.SHADOW, scene.getAura(AuraSlot.PAUSED).getPreset());
        assertEquals(AuraPreset.HOLY, scene.getAura(AuraSlot.NORMAL).getPreset());
        assertEquals(AuraPreset.FIRE, scene.getAura(AuraSlot.RAGED).getPreset());
        assertEquals(AuraPreset.ELECTRIC, scene.getAura(AuraSlot.ADVANTAGE).getPreset());
        assertEquals(AuraPreset.NEUTRAL, scene.getAura(AuraSlot.ON_DEMAND).getPreset());
    }

    @Test
    void getAuraReturnsTheSameInstanceOnceCreated() {
        Scene scene = new Scene("S");
        assertSame(scene.getAura(AuraSlot.RAGED), scene.getAura(AuraSlot.RAGED));
    }

    @Test
    void ensureAurasFillsAllFiveSlots() {
        Scene scene = new Scene("S");
        scene.ensureAuras();
        String json = new Gson().toJson(scene);
        for (AuraSlot slot : AuraSlot.values()) {
            assertTrue(json.contains("\"" + slot.name() + "\""), "missing slot " + slot);
        }
        assertEquals(5, AuraSlot.values().length);
    }

    @Test
    void setAuraIgnoresNulls() {
        Scene scene = new Scene("S");
        AuraConfig custom = AuraPreset.ELECTRIC.defaults();
        scene.setAura(AuraSlot.NORMAL, custom);
        scene.setAura(AuraSlot.NORMAL, null);
        scene.setAura(null, custom);
        assertSame(custom, scene.getAura(AuraSlot.NORMAL));
    }

    @Test
    void copyIsDeepForAurasBackgroundAndAssignments() {
        Scene a = new Scene("Original");
        a.setAssignment(TrackType.RAGED, "C:/m/r.mp3");
        a.getBackground().setPath("C:/bg.png");
        a.getBackground().setPlaybackRate(1.5);
        a.getAura(AuraSlot.RAGED).setIntensity(0.8);

        Scene b = a.copy();

        assertEquals(a.getId(), b.getId(), "copy keeps the id; duplicating callers assign a new one");
        assertEquals("Original", b.getName());
        assertEquals("C:/m/r.mp3", b.getAssignments().get(TrackType.RAGED));
        assertEquals("C:/bg.png", b.getBackground().getPath());
        assertEquals(1.5, b.getBackground().getPlaybackRate());
        assertEquals(0.8, b.getAura(AuraSlot.RAGED).getIntensity());

        assertNotSame(a.getBackground(), b.getBackground());
        assertNotSame(a.getAura(AuraSlot.RAGED), b.getAura(AuraSlot.RAGED));

        b.getBackground().setPath("C:/x.png");
        b.getAura(AuraSlot.RAGED).setIntensity(0.1);
        b.setAssignment(TrackType.NORMAL, "C:/m/n.mp3");
        assertEquals("C:/bg.png", a.getBackground().getPath());
        assertEquals(0.8, a.getAura(AuraSlot.RAGED).getIntensity());
        assertFalse(a.getAssignments().containsKey(TrackType.NORMAL));
    }

    @Test
    void backgroundForFallsBackToTheSceneDefault() {
        Scene scene = new Scene("S");
        scene.getBackground().setPath("C:/default.png");

        for (AuraSlot slot : AuraSlot.values()) {
            assertSame(scene.getBackground(), scene.backgroundFor(slot));
            assertFalse(scene.hasOwnBackground(slot));
        }
    }

    @Test
    void backgroundForReturnsTheSlotOverrideOnlyForThatSlot() {
        Scene scene = new Scene("S");
        scene.getBackground().setPath("C:/default.png");
        BackgroundConfig raged = new BackgroundConfig();
        raged.setPath("C:/raged.mp4");

        scene.setBackground(AuraSlot.RAGED, raged);

        assertSame(raged, scene.backgroundFor(AuraSlot.RAGED));
        assertTrue(scene.hasOwnBackground(AuraSlot.RAGED));
        assertSame(scene.getBackground(), scene.backgroundFor(AuraSlot.NORMAL));
    }

    @Test
    void blankPathOverrideFallsBack() {
        Scene scene = new Scene("S");
        BackgroundConfig blank = new BackgroundConfig();
        blank.setPath(" ");
        scene.setBackground(AuraSlot.PAUSED, blank);
        scene.setBackground(AuraSlot.NORMAL, new BackgroundConfig());

        assertFalse(scene.hasOwnBackground(AuraSlot.PAUSED));
        assertSame(scene.getBackground(), scene.backgroundFor(AuraSlot.PAUSED));
        assertSame(scene.getBackground(), scene.backgroundFor(AuraSlot.NORMAL));
    }

    @Test
    void clearBackgroundRemovesTheOverride() {
        Scene scene = new Scene("S");
        BackgroundConfig override = new BackgroundConfig();
        override.setPath("C:/a.png");
        scene.setBackground(AuraSlot.ADVANTAGE, override);

        scene.clearBackground(AuraSlot.ADVANTAGE);

        assertFalse(scene.hasOwnBackground(AuraSlot.ADVANTAGE));
        assertSame(scene.getBackground(), scene.backgroundFor(AuraSlot.ADVANTAGE));
        assertDoesNotThrow(() -> scene.clearBackground(AuraSlot.ADVANTAGE));
        assertDoesNotThrow(() -> scene.clearBackground(null));
    }

    @Test
    void setBackgroundIgnoresNulls() {
        Scene scene = new Scene("S");
        scene.setBackground(null, new BackgroundConfig());
        scene.setBackground(AuraSlot.RAGED, null);
        assertFalse(scene.hasOwnBackground(AuraSlot.RAGED));
    }

    @Test
    void setBackgroundForSlotReplacesTheExistingOverride() {
        Scene scene = new Scene("S");
        BackgroundConfig first = new BackgroundConfig();
        first.setPath("C:/first.png");
        BackgroundConfig second = new BackgroundConfig();
        second.setPath("C:/second.png");
        scene.setBackground(AuraSlot.RAGED, first);

        scene.setBackground(AuraSlot.RAGED, second);

        assertSame(second, scene.backgroundFor(AuraSlot.RAGED));
    }

    @Test
    void copyDuplicatesSlotOverridesIndependently() {
        Scene a = new Scene("S");
        BackgroundConfig override = new BackgroundConfig();
        override.setPath("C:/raged.png");
        a.setBackground(AuraSlot.RAGED, override);

        Scene b = a.copy();

        assertNotSame(override, b.backgroundFor(AuraSlot.RAGED));
        assertEquals("C:/raged.png", b.backgroundFor(AuraSlot.RAGED).getPath());
        b.backgroundFor(AuraSlot.RAGED).setPath("C:/other.png");
        b.clearBackground(AuraSlot.RAGED);
        assertEquals("C:/raged.png", a.backgroundFor(AuraSlot.RAGED).getPath());
    }

    @Test
    void sceneWithoutBackgroundsFieldFallsBackEverywhere() {
        Scene scene = new Gson().fromJson("{\"background\":{\"path\":\"C:/old.png\"}}", Scene.class);

        for (AuraSlot slot : AuraSlot.values()) {
            assertFalse(scene.hasOwnBackground(slot));
            assertEquals("C:/old.png", scene.backgroundFor(slot).getPath());
        }
        assertDoesNotThrow(scene::copy);
    }

    @Test
    void assignmentsRoundTripAndNullClears() {
        Scene scene = new Scene("S");
        scene.setAssignment(TrackType.PAUSED, "C:/m/p.mp3");
        scene.setAssignment(null, "C:/ignored.mp3");
        assertEquals(Map.of(TrackType.PAUSED, "C:/m/p.mp3"), scene.getAssignments());

        scene.setAssignment(TrackType.PAUSED, null);
        assertTrue(scene.getAssignments().isEmpty());
    }

    @Test
    void gettersAreNullSafeForPartialJson() {
        Scene scene = new Gson().fromJson("{}", Scene.class);

        assertNotNull(scene.getId());
        assertFalse(scene.getId().isBlank());
        assertEquals("Scene", scene.getName());
        assertTrue(scene.getAssignments().isEmpty());
        assertNotNull(scene.getBackground());
        assertNotNull(scene.getAura(AuraSlot.RAGED));
        assertDoesNotThrow(scene::copy);
        assertDoesNotThrow(() -> scene.setAssignment(TrackType.RAGED, "C:/x.mp3"));
    }

    @Test
    void nullAurasAndBlankNameAreTolerated() {
        Scene scene = new Gson().fromJson(
                "{\"id\":\" \",\"name\":\" \",\"auras\":{\"RAGED\":null},\"assignments\":{\"X\":\"y\",\"RAGED\":null}}",
                Scene.class);

        assertFalse(scene.getId().isBlank());
        assertEquals("Scene", scene.getName());
        assertEquals(AuraPreset.FIRE, scene.getAura(AuraSlot.RAGED).getPreset());
        assertTrue(scene.getAssignments().isEmpty());
        assertDoesNotThrow(scene::copy);
    }

    @Test
    void auraSlotMapsMoodsAndNull() {
        assertEquals(AuraSlot.PAUSED, AuraSlot.of(TrackType.PAUSED));
        assertEquals(AuraSlot.NORMAL, AuraSlot.of(TrackType.NORMAL));
        assertEquals(AuraSlot.RAGED, AuraSlot.of(TrackType.RAGED));
        assertEquals(AuraSlot.ADVANTAGE, AuraSlot.of(TrackType.ADVANTAGE));
        assertEquals(AuraSlot.ON_DEMAND, AuraSlot.of(null));
    }
}
