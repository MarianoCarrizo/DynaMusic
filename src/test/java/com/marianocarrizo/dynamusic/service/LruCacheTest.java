package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LruCacheTest {

    @Test
    void neverHoldsMoreThanItsCapacity() {
        LruCache<Integer, String> cache = new LruCache<>(3);

        for (int i = 0; i < 100; i++) cache.put(i, "v" + i);

        assertEquals(3, cache.size());
    }

    @Test
    void evictsTheOldestEntryFirst() {
        LruCache<Integer, String> cache = new LruCache<>(2);
        cache.put(1, "a");
        cache.put(2, "b");

        cache.put(3, "c");

        assertFalse(cache.contains(1));
        assertTrue(cache.contains(2));
        assertTrue(cache.contains(3));
    }

    @Test
    void readingAnEntryProtectsItFromEviction() {
        LruCache<Integer, String> cache = new LruCache<>(2);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.get(1);

        cache.put(3, "c");

        assertTrue(cache.contains(1));
        assertFalse(cache.contains(2));
    }

    @Test
    void missingKeyGivesNull() {
        assertNull(new LruCache<Integer, String>(2).get(9));
    }

    @Test
    void replacingAKeyKeepsTheSize() {
        LruCache<Integer, String> cache = new LruCache<>(2);
        cache.put(1, "a");
        cache.put(1, "b");

        assertEquals(1, cache.size());
        assertEquals("b", cache.get(1));
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<Integer, String>(0));
    }

    @Test
    void thumbnailCacheIsBoundedAt240() {
        assertEquals(240, CoverThumbnails.CACHE_SIZE);
    }
}
