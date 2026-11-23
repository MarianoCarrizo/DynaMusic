package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;

import java.util.Locale;

final class CacheUsageText {

    private static final long KILOBYTE = 1024;
    private static final long MEGABYTE = KILOBYTE * 1024;
    private static final long GIGABYTE = MEGABYTE * 1024;

    private CacheUsageText() {}

    static String usage(long usedBytes, CacheLimit limit) {
        return "Using " + size(usedBytes) + " of " + limit;
    }

    static String size(long bytes) {
        if (bytes >= GIGABYTE) return oneDecimal(bytes / (double) GIGABYTE) + " GB";
        if (bytes >= MEGABYTE) return Math.round(bytes / (double) MEGABYTE) + " MB";
        if (bytes >= KILOBYTE) return Math.round(bytes / (double) KILOBYTE) + " KB";
        return bytes + " B";
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
