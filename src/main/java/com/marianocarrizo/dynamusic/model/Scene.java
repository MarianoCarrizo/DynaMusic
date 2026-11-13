package com.marianocarrizo.dynamusic.model;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.service.TrackLibrary;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Scene {

    private String id;
    private String name;

    private Map<String, String> assignments;

    private BackgroundConfig background;

    private Map<String, BackgroundConfig> backgrounds;

    private Map<String, AuraConfig> auras;

    public Scene() {
        this.id = UUID.randomUUID().toString();
        this.assignments = new LinkedHashMap<>();
        this.background = new BackgroundConfig();
        this.auras = new LinkedHashMap<>();
    }

    public Scene(String name) {
        this();
        this.name = name;
    }

    public String getId() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        return id;
    }
    public void setId(String id) { this.id = id; }

    public String getName() { return (name != null && !name.isBlank()) ? name : "Scene"; }
    public void setName(String name) { this.name = name; }

    public Map<TrackType, String> getAssignments() {
        Map<TrackType, String> result = new EnumMap<>(TrackType.class);
        if (assignments == null) return result;
        assignments.forEach((key, value) -> {
            if (value == null) return;
            try {
                result.put(TrackType.valueOf(key), value);
            } catch (IllegalArgumentException ignored) {
            }
        });
        return result;
    }

    public void setAssignment(TrackType type, String path) {
        if (type == null) return;
        if (assignments == null) assignments = new LinkedHashMap<>();
        if (path == null) {
            assignments.remove(type.name());
        } else {
            assignments.put(type.name(), TrackLibrary.pin(path));
        }
    }

    public BackgroundConfig getBackground() {
        if (background == null) background = new BackgroundConfig();
        return background;
    }
    public void setBackground(BackgroundConfig b) { this.background = b; }

    public BackgroundConfig backgroundFor(AuraSlot slot) {
        return hasOwnBackground(slot) ? backgrounds.get(slot.name()) : getBackground();
    }

    public boolean hasOwnBackground(AuraSlot slot) {
        if (slot == null || backgrounds == null) return false;
        BackgroundConfig own = backgrounds.get(slot.name());
        return own != null && own.getPath() != null && !own.getPath().isBlank();
    }

    public void setBackground(AuraSlot slot, BackgroundConfig cfg) {
        if (slot == null || cfg == null) return;
        if (backgrounds == null) backgrounds = new LinkedHashMap<>();
        backgrounds.put(slot.name(), cfg);
    }

    public void clearBackground(AuraSlot slot) {
        if (slot == null || backgrounds == null) return;
        backgrounds.remove(slot.name());
    }

    public AuraConfig getAura(AuraSlot slot) {
        if (auras == null) auras = new LinkedHashMap<>();
        AuraConfig a = auras.get(slot.name());
        if (a == null) {
            a = defaultPreset(slot).defaults();
            auras.put(slot.name(), a);
        }
        return a;
    }

    public void setAura(AuraSlot slot, AuraConfig aura) {
        if (slot == null || aura == null) return;
        if (auras == null) auras = new LinkedHashMap<>();
        auras.put(slot.name(), aura);
    }

    public void ensureAuras() {
        for (AuraSlot s : AuraSlot.values()) getAura(s);
    }

    public static AuraPreset defaultPreset(AuraSlot slot) {
        return switch (slot) {
            case PAUSED    -> AuraPreset.SHADOW;
            case NORMAL    -> AuraPreset.HOLY;
            case RAGED     -> AuraPreset.FIRE;
            case ADVANTAGE -> AuraPreset.ELECTRIC;
            case ON_DEMAND -> AuraPreset.NEUTRAL;
        };
    }

    public Scene copy() {
        Scene s = new Scene();
        s.id = id;
        s.name = name;
        s.assignments = assignments != null ? new LinkedHashMap<>(assignments) : new LinkedHashMap<>();
        s.background = background != null ? background.copy() : new BackgroundConfig();
        s.backgrounds = new LinkedHashMap<>();
        if (backgrounds != null) {
            backgrounds.forEach((k, v) -> { if (v != null) s.backgrounds.put(k, v.copy()); });
        }
        s.auras = new LinkedHashMap<>();
        if (auras != null) {
            auras.forEach((k, v) -> { if (v != null) s.auras.put(k, v.copy()); });
        }
        return s;
    }
}
