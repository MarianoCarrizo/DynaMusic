package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.io.File;
import java.util.Optional;

public record SoundingTrack(String title, String detail, File file, boolean playing) {

    public static final SoundingTrack ABSENT = new SoundingTrack("", "", null, false);

    public SoundingTrack(String title, String detail, boolean playing) {
        this(title, detail, null, playing);
    }

    public static SoundingTrack of(File file, Optional<TrackTags> tags, boolean playing) {
        TrackLabels labels = TrackLabels.of(file, tags);
        return new SoundingTrack(labels.title(), labels.detail(), file, playing);
    }

    public static SoundingTrack of(String title, File file, Optional<TrackTags> tags, boolean playing) {
        return new SoundingTrack(title, TrackLabels.of(file, tags).detail(), file, playing);
    }

    public boolean isPresent() {
        return !title.isEmpty();
    }
}
