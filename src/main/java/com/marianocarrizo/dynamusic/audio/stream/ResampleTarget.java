package com.marianocarrizo.dynamusic.audio.stream;

import java.util.List;

public enum ResampleTarget {
    HZ_44100(44_100),
    HZ_48000(48_000);

    private static final int FAMILY_44100_BASE = 44_100;

    private final int rate;

    ResampleTarget(int rate) {
        this.rate = rate;
    }

    public int rate() {
        return rate;
    }

    public static List<ResampleTarget> preferenceFor(double sourceRate) {
        boolean family44100 = Math.round(sourceRate) % FAMILY_44100_BASE == 0;
        return family44100 ? List.of(HZ_44100, HZ_48000) : List.of(HZ_48000, HZ_44100);
    }
}
