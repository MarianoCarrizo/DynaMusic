package com.marianocarrizo.dynamusic.component;

record FillSpan(double start, double length) {

    static FillSpan horizontal(double trackStart, double trackLength, double thumbCenter) {
        double end = clamp(thumbCenter, trackStart, trackStart + trackLength);
        return new FillSpan(trackStart, end - trackStart);
    }

    static FillSpan vertical(double trackStart, double trackLength, double thumbCenter) {
        double trackEnd = trackStart + trackLength;
        double start = clamp(thumbCenter, trackStart, trackEnd);
        return new FillSpan(start, trackEnd - start);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
