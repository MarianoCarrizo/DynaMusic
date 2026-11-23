package com.marianocarrizo.dynamusic.model;

import java.util.Locale;

public enum RepeatMode {
    OFF("Repeat"),
    ALL("Repeat all"),
    ONE("Repeat one");

    private static final String LEGACY_ON = "true";
    private static final String LEGACY_OFF = "false";

    private final String hint;

    RepeatMode(String hint) {
        this.hint = hint;
    }

    public String hint() {
        return hint;
    }

    public boolean isActive() {
        return this != OFF;
    }

    public boolean repeatsTrack() {
        return this == ONE;
    }

    public static RepeatMode fromStored(String stored) {
        if (stored == null) return OFF;
        String text = stored.trim();
        if (LEGACY_ON.equalsIgnoreCase(text)) return ALL;
        if (LEGACY_OFF.equalsIgnoreCase(text)) return OFF;
        try {
            return valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return OFF;
        }
    }
}
