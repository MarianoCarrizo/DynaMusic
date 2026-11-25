package com.marianocarrizo.dynamusic.library;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class PublishGate {

    private static final int STANDARD_FILES = 500;
    private static final long STANDARD_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    private final int files;
    private final long intervalNanos;
    private final LongSupplier nanos;
    private int lastCount;
    private long lastTime;

    PublishGate(int files, long intervalNanos, LongSupplier nanos) {
        this.files = files;
        this.intervalNanos = intervalNanos;
        this.nanos = nanos;
        this.lastTime = nanos.getAsLong();
    }

    static PublishGate standard() {
        return new PublishGate(STANDARD_FILES, STANDARD_INTERVAL_NANOS, System::nanoTime);
    }

    boolean due(int count) {
        long now = nanos.getAsLong();
        if (count - lastCount < files && now - lastTime < intervalNanos) return false;
        lastCount = count;
        lastTime = now;
        return true;
    }
}
