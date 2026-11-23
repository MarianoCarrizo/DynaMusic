package com.marianocarrizo.dynamusic.audio.cache;

public enum CacheLimit {
    MB_512("512 MB", 512L * 1024 * 1024),
    GB_1("1 GB", 1024L * 1024 * 1024),
    GB_2("2 GB", 2L * 1024 * 1024 * 1024),
    GB_5("5 GB", 5L * 1024 * 1024 * 1024),
    GB_10("10 GB", 10L * 1024 * 1024 * 1024),
    GB_20("20 GB", 20L * 1024 * 1024 * 1024);

    public static final CacheLimit DEFAULT = GB_2;

    private final String label;
    private final long bytes;

    CacheLimit(String label, long bytes) {
        this.label = label;
        this.bytes = bytes;
    }

    public long bytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return label;
    }

    public static CacheLimit fromName(String name) {
        if (name == null) return DEFAULT;
        for (CacheLimit limit : values()) {
            if (limit.name().equals(name)) return limit;
        }
        return DEFAULT;
    }
}
