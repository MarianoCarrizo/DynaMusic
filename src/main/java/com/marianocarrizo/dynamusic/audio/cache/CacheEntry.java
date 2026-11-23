package com.marianocarrizo.dynamusic.audio.cache;

record CacheEntry(String key, double gain, long bytes, long lastAccess) {

    boolean hasWav() {
        return bytes > 0;
    }

    CacheEntry touched(long when) {
        return new CacheEntry(key, gain, bytes, when);
    }
}
