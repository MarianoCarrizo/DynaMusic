package com.marianocarrizo.dynamusic.model;

import java.util.Locale;

public enum AppMode {
    LISTEN("Listen"),
    SESSION("Session");

    private final String label;

    AppMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String storedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AppMode fromStored(String stored) {
        if (stored == null) return SESSION;
        try {
            return valueOf(stored.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return SESSION;
        }
    }
}
