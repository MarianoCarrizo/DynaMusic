package com.marianocarrizo.dynamusic.audio.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class CacheIndexCodec {

    static final String HEADER = "dynamusic-audio-cache 1";
    private static final String FIELD_SEPARATOR = "\t";
    private static final String LINE_SEPARATOR = "\n";
    private static final int FIELD_COUNT = 4;
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{64}");

    private CacheIndexCodec() {}

    static String encode(Collection<CacheEntry> entries) {
        StringBuilder text = new StringBuilder(HEADER).append(LINE_SEPARATOR);
        for (CacheEntry entry : entries) {
            text.append(entry.key()).append(FIELD_SEPARATOR).append(entry.gain()).append(FIELD_SEPARATOR)
                    .append(entry.bytes()).append(FIELD_SEPARATOR).append(entry.lastAccess())
                    .append(LINE_SEPARATOR);
        }
        return text.toString();
    }

    static List<CacheEntry> decode(String text) {
        List<CacheEntry> entries = new ArrayList<>();
        if (text == null) return entries;
        String[] lines = text.split("\r?\n");
        if (lines.length == 0 || !HEADER.equals(lines[0])) return entries;
        for (int i = 1; i < lines.length; i++) {
            parse(lines[i]).ifPresent(entries::add);
        }
        return entries;
    }

    private static Optional<CacheEntry> parse(String line) {
        String[] fields = line.split(FIELD_SEPARATOR, -1);
        if (fields.length != FIELD_COUNT || !KEY.matcher(fields[0]).matches()) return Optional.empty();
        try {
            double gain = Double.parseDouble(fields[1]);
            long bytes = Long.parseLong(fields[2]);
            long lastAccess = Long.parseLong(fields[3]);
            boolean valid = gain > 0 && Double.isFinite(gain) && bytes >= 0 && lastAccess >= 0;
            return valid ? Optional.of(new CacheEntry(fields[0], gain, bytes, lastAccess))
                    : Optional.empty();
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
