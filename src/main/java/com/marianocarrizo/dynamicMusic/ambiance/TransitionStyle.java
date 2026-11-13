package com.marianocarrizo.dynamicMusic.ambiance;

import com.marianocarrizo.dynamicMusic.model.AuraPreset;

public enum TransitionStyle {
    FIRE(800, Overlay.SOLID, 0xFF7A1A, 0, 0.4, 0.6),
    ELECTRIC(350, Overlay.SOLID, 0xCFF6FF, 3, 0.0, 0.9),
    HOLY(1000, Overlay.SOLID, 0xFFF8E0, 0, 0.5, 0.75),
    SHADOW(1200, Overlay.VIGNETTE, 0x000000, 0, 0.5, 1.0),
    NEUTRAL(600, Overlay.NONE, 0xFFFFFF, 0, 0.0, 0.0);

    public enum Overlay { NONE, SOLID, VIGNETTE }

    private static final double CROSSFADE_HALF_WIDTH = 0.25;

    private final int durationMillis;
    private final Overlay overlay;
    private final int overlayRgb;
    private final int flashCount;
    private final double peakAt;
    private final double peakOpacity;

    TransitionStyle(int durationMillis, Overlay overlay, int overlayRgb,
                    int flashCount, double peakAt, double peakOpacity) {
        this.durationMillis = durationMillis;
        this.overlay = overlay;
        this.overlayRgb = overlayRgb;
        this.flashCount = flashCount;
        this.peakAt = peakAt;
        this.peakOpacity = peakOpacity;
    }

    public static TransitionStyle forPreset(AuraPreset preset) {
        if (preset == null) return NEUTRAL;
        return switch (preset) {
            case FIRE     -> FIRE;
            case ELECTRIC -> ELECTRIC;
            case HOLY     -> HOLY;
            case SHADOW   -> SHADOW;
            case NEUTRAL  -> NEUTRAL;
        };
    }

    public int durationMillis() { return durationMillis; }

    public Overlay overlay() { return overlay; }

    public int overlayRgb() { return overlayRgb; }

    public int flashCount() { return flashCount; }

    public double incomingOpacity(double progress) {
        double t = clamp(progress);
        return switch (overlay) {
            case NONE     -> t;
            case VIGNETTE -> t >= peakAt ? 1.0 : 0.0;
            case SOLID    -> flashCount > 0
                    ? (t >= lastFlashCentre() ? 1.0 : 0.0)
                    : smoothstep(peakAt - CROSSFADE_HALF_WIDTH, peakAt + CROSSFADE_HALF_WIDTH, t);
        };
    }

    public double overlayOpacity(double progress) {
        double t = clamp(progress);
        return switch (overlay) {
            case NONE     -> 0.0;
            case VIGNETTE -> 1.0;
            case SOLID    -> peakOpacity * (flashCount > 0 ? flashLevel(t) : ramp(t));
        };
    }

    public double vignetteOpenness(double progress) {
        double t = clamp(progress);
        if (overlay != Overlay.VIGNETTE) return 1.0;
        return t < peakAt ? 1.0 - smoothstep(0.0, peakAt, t) : smoothstep(peakAt, 1.0, t);
    }

    private double ramp(double t) {
        return t < peakAt ? t / peakAt : (1.0 - t) / (1.0 - peakAt);
    }

    private double flashLevel(double t) {
        double halfWidth = 0.5 / (flashCount + 0.5);
        double level = 0.0;
        for (int i = 0; i < flashCount; i++) {
            double centre = (i + 0.5) / (flashCount + 0.5);
            level = Math.max(level, 1.0 - Math.abs(t - centre) / halfWidth);
        }
        return Math.max(level, 0.0);
    }

    private double lastFlashCentre() {
        return (flashCount - 0.5) / (flashCount + 0.5);
    }

    private static double smoothstep(double from, double to, double t) {
        double x = clamp((t - from) / (to - from));
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
