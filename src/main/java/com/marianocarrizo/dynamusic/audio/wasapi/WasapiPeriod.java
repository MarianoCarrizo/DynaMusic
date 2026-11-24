package com.marianocarrizo.dynamusic.audio.wasapi;

public final class WasapiPeriod {

    public static final long DEFAULT_HNS = 200_000;
    private static final double HNS_PER_SECOND = 10_000_000.0;

    private WasapiPeriod() {
    }

    static long target(long requestedHns, long minimumHns) {
        return Math.max(requestedHns, minimumHns);
    }

    static long aligned(int frames, int sampleRate) {
        return Math.round(HNS_PER_SECOND * frames / sampleRate);
    }
}
