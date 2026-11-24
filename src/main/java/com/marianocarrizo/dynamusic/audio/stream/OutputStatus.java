package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

public record OutputStatus(OutputRoute route, int bits, int rate, boolean attenuated) {

    public static final OutputStatus NONE = new OutputStatus(OutputRoute.NONE, 0, 0, false);

    private static final int HZ_PER_KHZ = 1000;
    private static final String NOT_BIT_PERFECT = " \u00b7 not bit-perfect";

    public static OutputStatus of(OutputRoute route, AudioFormat format) {
        return new OutputStatus(route, format.getSampleSizeInBits(), Math.round(format.getSampleRate()), false);
    }

    public OutputStatus withAttenuation(boolean attenuated) {
        return new OutputStatus(route, bits, rate, attenuated);
    }

    public boolean bitPerfect() {
        return route == OutputRoute.EXCLUSIVE && !attenuated;
    }

    public String label() {
        return switch (route) {
            case NONE -> "";
            case SHARED -> format() + " shared";
            case WASAPI_SHARED -> format() + " shared (WASAPI)";
            case EXCLUSIVE -> format() + " exclusive" + (attenuated ? NOT_BIT_PERFECT : "");
        };
    }

    private String format() {
        String kilohertz = rate % HZ_PER_KHZ == 0
                ? Integer.toString(rate / HZ_PER_KHZ)
                : Double.toString((double) rate / HZ_PER_KHZ);
        return bits + "/" + kilohertz;
    }
}
