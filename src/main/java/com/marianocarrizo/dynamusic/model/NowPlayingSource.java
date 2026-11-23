package com.marianocarrizo.dynamusic.model;

public enum NowPlayingSource {
    MOOD(true),
    PLAYLIST(true),
    ON_DEMAND(false),
    PREVIEW(false),
    NONE(false);

    private final boolean named;

    NowPlayingSource(boolean named) {
        this.named = named;
    }

    public boolean isNamed() {
        return named;
    }
}
