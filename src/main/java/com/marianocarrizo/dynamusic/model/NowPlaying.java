package com.marianocarrizo.dynamusic.model;

import java.io.File;

public record NowPlaying(NowPlayingSource source, String sourceName, String title, String detail, File file,
                         boolean playing, LoadState state) {

    public static final NowPlaying NONE = new NowPlaying(NowPlayingSource.NONE, "", "Nothing playing", "", false);

    public NowPlaying {
        sourceName = source.isNamed() && sourceName != null ? sourceName : "";
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
        state = state == null ? LoadState.READY : state;
        playing = source != NowPlayingSource.NONE && playing && state == LoadState.READY;
    }

    public NowPlaying(NowPlayingSource source, String sourceName, String title, String detail, File file,
                      boolean playing) {
        this(source, sourceName, title, detail, file, playing, LoadState.READY);
    }

    public NowPlaying(NowPlayingSource source, String sourceName, String title, String detail, boolean playing) {
        this(source, sourceName, title, detail, null, playing, LoadState.READY);
    }

    public String badge() {
        return source.badge(sourceName);
    }

    public boolean isSounding(NowPlayingSource expected) {
        return playing && source == expected;
    }

    public boolean isLoading() {
        return state == LoadState.LOADING;
    }

    public boolean isBusy() {
        return state.isBusy();
    }
}
