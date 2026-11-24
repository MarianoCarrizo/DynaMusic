package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class IndexCacheCodec {

    static final String HEADER = "dynamusic-library-index 2";
    private static final String FIELD_SEPARATOR = "\t";
    private static final String LINE_SEPARATOR = "\n";
    private static final int FIELD_COUNT = 9;
    private static final String COVER = "1";
    private static final String NO_COVER = "0";

    private IndexCacheCodec() {}

    static String encode(Collection<TrackEntry> entries) {
        StringBuilder text = new StringBuilder(HEADER).append(LINE_SEPARATOR);
        for (TrackEntry entry : entries) {
            TrackTags tags = entry.tags();
            text.append(escape(entry.path())).append(FIELD_SEPARATOR)
                    .append(entry.size()).append(FIELD_SEPARATOR)
                    .append(entry.modified()).append(FIELD_SEPARATOR)
                    .append(tags.trackNumber()).append(FIELD_SEPARATOR)
                    .append(tags.hasCover() ? COVER : NO_COVER).append(FIELD_SEPARATOR)
                    .append(escape(tags.year())).append(FIELD_SEPARATOR)
                    .append(escape(tags.title())).append(FIELD_SEPARATOR)
                    .append(escape(tags.artist())).append(FIELD_SEPARATOR)
                    .append(escape(tags.album())).append(LINE_SEPARATOR);
        }
        return text.toString();
    }

    static boolean hasValidHeader(String text) {
        return text != null && text.split("\r?\n", 2)[0].equals(HEADER);
    }

    static List<TrackEntry> decode(String text) {
        List<TrackEntry> entries = new ArrayList<>();
        if (!hasValidHeader(text)) return entries;
        String[] lines = text.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {
            parse(lines[i]).ifPresent(entries::add);
        }
        return entries;
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case '\\' -> out.append('\\');
                default -> out.append(c).append(next);
            }
        }
        return out.toString();
    }

    private static Optional<TrackEntry> parse(String line) {
        String[] fields = line.split(FIELD_SEPARATOR, -1);
        if (fields.length != FIELD_COUNT || fields[0].isEmpty()) return Optional.empty();
        boolean coverFlag = COVER.equals(fields[4]);
        if (!coverFlag && !NO_COVER.equals(fields[4])) return Optional.empty();
        try {
            long size = Long.parseLong(fields[1]);
            long modified = Long.parseLong(fields[2]);
            int trackNumber = Integer.parseInt(fields[3]);
            if (size < 0 || trackNumber < 0) return Optional.empty();
            TrackTags tags = new TrackTags(unescape(fields[6]), unescape(fields[7]), unescape(fields[8]), trackNumber,
                    unescape(fields[5]), coverFlag);
            return Optional.of(new TrackEntry(unescape(fields[0]), size, modified, tags));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
