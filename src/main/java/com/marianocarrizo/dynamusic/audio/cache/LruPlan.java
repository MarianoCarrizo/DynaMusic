package com.marianocarrizo.dynamusic.audio.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class LruPlan {

    static final int MAX_GAIN_ONLY_ENTRIES = 20000;

    private static final Comparator<CacheEntry> OLDEST_FIRST =
            Comparator.comparingLong(CacheEntry::lastAccess).thenComparing(CacheEntry::key);

    private LruPlan() {}

    static List<String> wavVictims(Collection<CacheEntry> entries, long limitBytes, Set<String> excluded) {
        long total = entries.stream().mapToLong(CacheEntry::bytes).sum();
        List<String> victims = new ArrayList<>();
        for (CacheEntry entry : entries.stream().sorted(OLDEST_FIRST).toList()) {
            if (total <= limitBytes) break;
            if (!entry.hasWav() || excluded.contains(entry.key())) continue;
            victims.add(entry.key());
            total -= entry.bytes();
        }
        return victims;
    }

    static List<String> gainOnlyVictims(Collection<CacheEntry> entries) {
        List<CacheEntry> gainOnly = entries.stream().filter(entry -> !entry.hasWav()).sorted(OLDEST_FIRST).toList();
        int surplus = gainOnly.size() - MAX_GAIN_ONLY_ENTRIES;
        if (surplus <= 0) return List.of();
        return gainOnly.stream().limit(surplus).map(CacheEntry::key).toList();
    }
}
