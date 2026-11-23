package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlayingSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

final class RepeatSettings {

    private final Map<NowPlayingSource, Consumer<Boolean>> targets = new EnumMap<>(NowPlayingSource.class);
    private final Map<NowPlayingSource, Boolean> state = new EnumMap<>(NowPlayingSource.class);

    RepeatSettings(Consumer<Boolean> mood, Consumer<Boolean> playlist, Consumer<Boolean> onDemand,
                   Consumer<Boolean> preview) {
        targets.put(NowPlayingSource.MOOD, mood);
        targets.put(NowPlayingSource.PLAYLIST, playlist);
        targets.put(NowPlayingSource.ON_DEMAND, onDemand);
        targets.put(NowPlayingSource.PREVIEW, preview);
    }

    boolean isOn(NowPlayingSource source) {
        return state.getOrDefault(source, false);
    }

    void set(NowPlayingSource source, boolean on) {
        Consumer<Boolean> target = targets.get(source);
        if (target == null) return;
        state.put(source, on);
        target.accept(on);
    }
}
