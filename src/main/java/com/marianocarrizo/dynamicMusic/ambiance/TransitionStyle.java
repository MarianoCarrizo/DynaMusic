package com.marianocarrizo.dynamicMusic.ambiance;

import com.marianocarrizo.dynamicMusic.model.AuraPreset;

import java.util.regex.Pattern;

public enum TransitionStyle {
    FIRE(800, Overlay.SOLID, ColorSource.PRIMARY, 0.0, 0xFF7A1A, 0, 0.4, 0.6),
    ELECTRIC(350, Overlay.SOLID, ColorSource.PRIMARY_LIGHTENED, 0.6, 0xCFF6FF, 3, 0.0, 0.9),
    HOLY(1000, Overlay.SOLID, ColorSource.PRIMARY_LIGHTENED, 0.6, 0xFFF8E0, 0, 0.5, 0.75),
    SHADOW(1200, Overlay.VIGNETTE, ColorSource.SECONDARY, 0.0, 0x000000, 0, 0.5, 1.0),
    NEUTRAL(600, Overlay.NONE, ColorSource.NONE, 0.0, 0xFFFFFF, 0, 0.0, 0.0);

    public enum Overlay { NONE, SOLID, VIGNETTE }

    public enum ColorSource { NONE, PRIMARY, PRIMARY_LIGHTENED, SECONDARY }

    private static final double CROSSFADE_HALF_WIDTH = 0.25;
    private static final Pattern HEX_DIGITS = Pattern.compile("[0-9A-Fa-f]{6}");

    private final int durationMillis;
    private final Overlay overlay;
    private final ColorSource colorSource;
    private final double lighten;
    private final int fallbackRgb;
    private final int flashCount;
    private final double peakAt;
    private final double peakOpacity;

    TransitionStyle(int durationMillis, Overlay overlay, ColorSource colorSource, double lighten,
                    int fallbackRgb, int flashCount, double peakAt, double peakOpacity) {
        this.durationMillis = durationMillis;
        this.overlay = overlay;
        this.colorSource = colorSource;
        this.lighten = lighten;
        this.fallbackRgb = fallbackRgb;
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

    public ColorSource colorSource() { return colorSource; }

    public int overlayRgb(String primaryHex, String secondaryHex) {
        return switch (colorSource) {
            case NONE              -> fallbackRgb;
            case PRIMARY           -> parseHex(primaryHex, fallbackRgb);
            case PRIMARY_LIGHTENED -> lightened(parseHex(primaryHex, -1));
            case SECONDARY         -> parseHex(secondaryHex, fallbackRgb);
        };
    }

    private int lightened(int rgb) {
        if (rgb < 0) return fallbackRgb;
        int red = mixTowardWhite((rgb >> 16) & 0xFF);
        int green = mixTowardWhite((rgb >> 8) & 0xFF);
        int blue = mixTowardWhite(rgb & 0xFF);
        return (red << 16) | (green << 8) | blue;
    }

    private int mixTowardWhite(int channel) {
        return (int) Math.round(channel + (255 - channel) * lighten);
    }

    private static int parseHex(String hex, int fallback) {
        if (hex == null) return fallback;
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!HEX_DIGITS.matcher(digits).matches()) return fallback;
        return Integer.parseInt(digits, 16);
    }

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
