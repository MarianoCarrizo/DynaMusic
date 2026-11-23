package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.RepeatMode;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

final class RepeatSettings {

    private final Map<NowPlayingSource, Consumer<RepeatMode>> targets = new EnumMap<>(NowPlayingSource.class);
    private final Map<NowPlayingSource, RepeatMode> state = new EnumMap<>(NowPlayingSource.class);

    RepeatSettings(Consumer<RepeatMode> mood, Consumer<RepeatMode> playlist, Consumer<RepeatMode> onDemand,
                   Consumer<RepeatMode> preview) {
        targets.put(NowPlayingSource.MOOD, mood);
        targets.put(NowPlayingSource.PLAYLIST, playlist);
        targets.put(NowPlayingSource.ON_DEMAND, onDemand);
        targets.put(NowPlayingSource.PREVIEW, preview);
    }

    RepeatMode modeOf(NowPlayingSource source) {
        return state.getOrDefault(source, RepeatMode.OFF);
    }

    RepeatMode cycle(NowPlayingSource source) {
        set(source, source.nextRepeat(modeOf(source)));
        return modeOf(source);
    }

    void set(NowPlayingSource source, RepeatMode mode) {
        Consumer<RepeatMode> target = targets.get(source);
        if (target == null) return;
        RepeatMode supported = source.supportedRepeat(mode);
        state.put(source, supported);
        target.accept(supported);
    }
}
