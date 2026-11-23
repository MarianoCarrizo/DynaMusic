package com.marianocarrizo.dynamusic.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import org.junit.jupiter.api.Test;

class CacheUsageTextTest {

    @Test
    void sizesUseTheLargestFittingUnit() {
        assertEquals("0 B", CacheUsageText.size(0));
        assertEquals("512 B", CacheUsageText.size(512));
        assertEquals("2 KB", CacheUsageText.size(2048));
        assertEquals("320 MB", CacheUsageText.size(320L * 1024 * 1024));
        assertEquals("1.5 GB", CacheUsageText.size(1536L * 1024 * 1024));
    }

    @Test
    void usageNamesTheLimit() {
        assertEquals("Using 1.5 GB of 2 GB", CacheUsageText.usage(1536L * 1024 * 1024, CacheLimit.GB_2));
        assertEquals("Using 0 B of 512 MB", CacheUsageText.usage(0, CacheLimit.MB_512));
    }
}
