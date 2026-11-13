package com.marianocarrizo.dynamicMusic.Controller;

import com.marianocarrizo.dynamicMusic.model.AuraSlot;
import com.marianocarrizo.dynamicMusic.model.BackgroundConfig;
import com.marianocarrizo.dynamicMusic.model.Scene;

enum BackgroundTarget {
    SCENE_DEFAULT("Scene default", null),
    PAUSED("Paused", AuraSlot.PAUSED),
    NORMAL("Normal", AuraSlot.NORMAL),
    RAGED("Raged", AuraSlot.RAGED),
    ADVANTAGE("Advantage", AuraSlot.ADVANTAGE),
    ON_DEMAND("On demand", AuraSlot.ON_DEMAND);

    private final String label;
    private final AuraSlot slot;

    BackgroundTarget(String label, AuraSlot slot) {
        this.label = label;
        this.slot = slot;
    }

    static BackgroundTarget of(AuraSlot slot) {
        for (BackgroundTarget target : values()) {
            if (target.slot == slot) return target;
        }
        return SCENE_DEFAULT;
    }

    String label() { return label; }

    AuraSlot slot() { return slot; }

    BackgroundConfig configIn(Scene scene) {
        if (slot == null) return scene.getBackground();
        return scene.hasOwnBackground(slot) ? scene.backgroundFor(slot) : null;
    }

    BackgroundConfig editableIn(Scene scene) {
        BackgroundConfig existing = configIn(scene);
        if (existing != null) return existing;
        BackgroundConfig created = new BackgroundConfig();
        created.setFitMode(scene.getBackground().getFitMode());
        scene.setBackground(slot, created);
        return created;
    }

    void clearIn(Scene scene) {
        if (slot == null) {
            BackgroundConfig cleared = new BackgroundConfig();
            cleared.setFitMode(scene.getBackground().getFitMode());
            scene.setBackground(cleared);
        } else {
            scene.clearBackground(slot);
        }
    }
}
