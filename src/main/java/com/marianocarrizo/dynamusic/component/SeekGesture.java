package com.marianocarrizo.dynamusic.component;

import java.util.OptionalDouble;

final class SeekGesture {

    static final double LANDING_TOLERANCE_SECONDS = 1.0;
    static final long LANDING_TIMEOUT_MILLIS = 1500;

    private boolean dragging;
    private boolean awaitingLanding;
    private double target;
    private long seekedAtMillis;

    static double fractionAt(double x, double trackStart, double trackLength) {
        if (trackLength <= 0) return 0;
        return Math.max(0, Math.min(1, (x - trackStart) / trackLength));
    }

    void press(double fraction) {
        dragging = true;
        awaitingLanding = false;
        target = fraction;
    }

    void drag(double fraction) {
        if (dragging) target = fraction;
    }

    OptionalDouble release(double fraction, long nowMillis) {
        if (!dragging) return OptionalDouble.empty();
        dragging = false;
        return jump(fraction, nowMillis);
    }

    OptionalDouble jump(double fraction, long nowMillis) {
        target = fraction;
        awaitingLanding = true;
        seekedAtMillis = nowMillis;
        return OptionalDouble.of(fraction);
    }

    boolean accepts(double currentSeconds, double totalSeconds, long nowMillis) {
        if (dragging) return false;
        if (!awaitingLanding) return true;
        boolean landed = Math.abs(currentSeconds - target * totalSeconds) <= LANDING_TOLERANCE_SECONDS;
        boolean expired = nowMillis - seekedAtMillis >= LANDING_TIMEOUT_MILLIS;
        if (landed || expired) awaitingLanding = false;
        return !awaitingLanding;
    }

    void cancel() {
        dragging = false;
        awaitingLanding = false;
    }

    boolean isDragging() {
        return dragging;
    }

    double target() {
        return target;
    }
}
