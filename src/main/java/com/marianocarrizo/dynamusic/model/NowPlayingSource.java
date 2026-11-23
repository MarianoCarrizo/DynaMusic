package com.marianocarrizo.dynamusic.model;

import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

public enum NowPlayingSource {
    MOOD("Mood", true, NowPlayingSource::sentenceCase, RepeatMode.OFF, RepeatMode.ONE),
    PLAYLIST("Playlist", true, name -> name, RepeatMode.OFF, RepeatMode.ALL, RepeatMode.ONE),
    ON_DEMAND("Playing now", false, name -> name, RepeatMode.OFF, RepeatMode.ONE),
    PREVIEW("Preview", false, name -> name, RepeatMode.OFF, RepeatMode.ONE),
    NONE("", false, name -> name, RepeatMode.OFF);

    private static final String BADGE_SEPARATOR = " · ";

    private final String label;
    private final boolean named;
    private final UnaryOperator<String> nameFormat;
    private final List<RepeatMode> repeatCycle;

    NowPlayingSource(String label, boolean named, UnaryOperator<String> nameFormat, RepeatMode... repeatCycle) {
        this.label = label;
        this.named = named;
        this.nameFormat = nameFormat;
        this.repeatCycle = List.of(repeatCycle);
    }

    public boolean isRepeatable() {
        return repeatCycle.size() > 1;
    }

    public RepeatMode supportedRepeat(RepeatMode mode) {
        if (repeatCycle.contains(mode)) return mode;
        if (mode == RepeatMode.ALL && repeatCycle.contains(RepeatMode.ONE)) return RepeatMode.ONE;
        return RepeatMode.OFF;
    }

    public RepeatMode nextRepeat(RepeatMode current) {
        int index = repeatCycle.indexOf(supportedRepeat(current));
        return repeatCycle.get((index + 1) % repeatCycle.size());
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
