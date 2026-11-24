package com.marianocarrizo.dynamusic.service.playback;

public enum PlaybackUse {
    MOOD(false),
    LISTEN(true);

    private final boolean streamable;

    PlaybackUse(boolean streamable) {
        this.streamable = streamable;
    }

    public boolean streamable() {
        return streamable;
    }
}
