package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.NameNormalizer;

import java.util.List;
import java.util.function.LongSupplier;

final class TypeAheadMatcher {

    static final int NOT_FOUND = -1;

    private final LongSupplier clock;
    private final long timeoutMillis;
    private final StringBuilder buffer = new StringBuilder();
    private long lastTyped;

    TypeAheadMatcher(LongSupplier clock, long timeoutMillis) {
        this.clock = clock;
        this.timeoutMillis = timeoutMillis;
    }

    boolean hasPending() {
        return buffer.length() > 0 && !expired(clock.getAsLong());
    }

    int next(List<String> names, int current, char typed) {
        long now = clock.getAsLong();
        if (expired(now)) buffer.setLength(0);
        buffer.append(typed);
        lastTyped = now;
        boolean cycling = isRepeatedLetter();
        String prefix = NameNormalizer.normalize(cycling ? String.valueOf(typed) : buffer.toString());
        int start = cycling || buffer.length() == 1 ? current + 1 : Math.max(current, 0);
        return find(names, prefix, start);
    }

    private boolean expired(long now) {
        return now - lastTyped > timeoutMillis;
    }

    private boolean isRepeatedLetter() {
        if (buffer.length() < 2) return false;
        char first = buffer.charAt(0);
        for (int i = 1; i < buffer.length(); i++) {
            if (Character.toLowerCase(buffer.charAt(i)) != Character.toLowerCase(first)) return false;
        }
        return true;
    }

    private static int find(List<String> names, String prefix, int start) {
        int size = names.size();
        for (int offset = 0; offset < size; offset++) {
            int index = Math.floorMod(start + offset, size);
            if (NameNormalizer.normalize(names.get(index)).startsWith(prefix)) return index;
        }
        return NOT_FOUND;
    }
}
