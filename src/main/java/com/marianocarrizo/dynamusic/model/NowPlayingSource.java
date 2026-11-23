package com.marianocarrizo.dynamusic.model;

public enum NowPlayingSource {
    MOOD("MOOD", true),
    PLAYLIST("PLAYLIST", true),
    ON_DEMAND("PLAYING NOW", false),
    PREVIEW("PREVIEW", false),
    NONE("", false);

    private static final String BADGE_SEPARATOR = " · ";

    private final String label;
    private final boolean named;

    NowPlayingSource(String label, boolean named) {
        this.label = label;
        this.named = named;
    }

    public boolean isNamed() {
        return named;
    }

    public String badge(String sourceName) {
        if (!named || sourceName == null || sourceName.isEmpty()) return label;
        return label + BADGE_SEPARATOR + sourceName;
    }
}
