package com.marianocarrizo.dynamusic.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LruCache<K, V> {

    private final Map<K, V> entries;

    public LruCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive: " + capacity);
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(K key) {
        return entries.get(key);
    }

    public void put(K key, V value) {
        entries.put(key, value);
    }

    public boolean contains(K key) {
        return entries.containsKey(key);
    }

    public int size() {
        return entries.size();
    }
}
