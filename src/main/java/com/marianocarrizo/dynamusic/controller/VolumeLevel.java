package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.Icons;

enum VolumeLevel {
    MUTED(Icons.VOLUME_OFF),
    LOW(Icons.VOLUME_LOW),
    HIGH(Icons.VOLUME_HIGH);

    private static final double HIGH_FROM = 0.5;

    private final String iconPath;

    VolumeLevel(String iconPath) {
        this.iconPath = iconPath;
    }

    String iconPath() {
        return iconPath;
    }

    static VolumeLevel of(double volume) {
        if (volume <= 0) return MUTED;
        return volume < HIGH_FROM ? LOW : HIGH;
    }
}
