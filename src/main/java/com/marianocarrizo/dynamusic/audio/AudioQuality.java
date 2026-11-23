package com.marianocarrizo.dynamusic.audio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record AudioQuality(String format, int bitsPerSample, boolean floatingPoint, int sampleRate,
                           int bitrateKbps) {

    static final int HI_RES_BITS_ABOVE = 16;
    static final int HI_RES_RATE_ABOVE = 48000;
    private static final String SEPARATOR = " · ";
    private static final BigDecimal HZ_PER_KHZ = BigDecimal.valueOf(1000);

    public static AudioQuality ofFormat(String format) {
        return new AudioQuality(format, 0, false, 0, 0);
    }

    public String label() {
        List<String> parts = new ArrayList<>();
        parts.add(format);
        if (bitsPerSample > 0) parts.add(bitsPerSample + "-bit" + (floatingPoint ? " float" : ""));
        if (sampleRate > 0) parts.add(kilohertz(sampleRate));
        if (bitrateKbps > 0) parts.add(bitrateKbps + " kbps");
        return String.join(SEPARATOR, parts);
    }

    public boolean isHiRes() {
        return bitsPerSample > HI_RES_BITS_ABOVE || sampleRate > HI_RES_RATE_ABOVE;
    }

    static String kilohertz(int hertz) {
        BigDecimal khz = BigDecimal.valueOf(hertz).divide(HZ_PER_KHZ).stripTrailingZeros();
        return khz.toPlainString() + " kHz";
    }
}
