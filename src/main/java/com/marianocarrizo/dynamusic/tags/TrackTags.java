package com.marianocarrizo.dynamusic.tags;

public record TrackTags(String title, String artist, String album, int trackNumber, String year, boolean hasCover) {

    public static final TrackTags EMPTY = new TrackTags("", "", "", 0, "", false);

    private static final char BYTE_ORDER_MARK = '﻿';

    public TrackTags {
        title = clean(title);
        artist = clean(artist);
        album = clean(album);
        year = clean(year);
        trackNumber = Math.max(trackNumber, 0);
    }

    public static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (!Character.isISOControl(character) && character != BYTE_ORDER_MARK) cleaned.append(character);
        }
        return cleaned.toString().trim();
    }
}
