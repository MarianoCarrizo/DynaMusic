package com.marianocarrizo.dynamusic.model;

import java.util.Locale;
import java.util.function.UnaryOperator;

public enum NowPlayingSource {
    MOOD("Mood", true, NowPlayingSource::sentenceCase),
    PLAYLIST("Playlist", true, name -> name),
    ON_DEMAND("Playing now", false, name -> name),
    PREVIEW("Preview", false, name -> name),
    NONE("", false, name -> name);

    private static final String BADGE_SEPARATOR = " · ";

    private final String label;
    private final boolean named;
    private final UnaryOperator<String> nameFormat;

    NowPlayingSource(String label, boolean named, UnaryOperator<String> nameFormat) {
        this.label = label;
        this.named = named;
        this.nameFormat = nameFormat;
    }

    public boolean isNamed() {
        return named;
    }

    public String badge(String sourceName) {
        if (!named || sourceName == null || sourceName.isEmpty()) return label;
        return label + BADGE_SEPARATOR + nameFormat.apply(sourceName);
    }

    private static String sentenceCase(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
