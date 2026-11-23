package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.LoadState;
import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.io.File;
import java.util.Optional;

public record SoundingTrack(String title, String detail, File file, boolean playing, LoadState state) {

    public static final SoundingTrack ABSENT = new SoundingTrack("", "", null, false);

    public SoundingTrack {
        state = state == null ? LoadState.READY : state;
        playing = playing && state == LoadState.READY;
    }

    public SoundingTrack(String title, String detail, File file, boolean playing) {
        this(title, detail, file, playing, LoadState.READY);
    }

    public SoundingTrack(String title, String detail, boolean playing) {
        this(title, detail, null, playing, LoadState.READY);
    }

    public static SoundingTrack of(File file, Optional<TrackTags> tags, boolean playing) {
        TrackLabels labels = TrackLabels.of(file, tags);
        return new SoundingTrack(labels.title(), labels.detail(), file, playing);
    }

    public static SoundingTrack of(String title, File file, Optional<TrackTags> tags, boolean playing) {
        return new SoundingTrack(title, TrackLabels.of(file, tags).detail(), file, playing);
    }

    public SoundingTrack withState(LoadState newState, String detailOverride) {
        String newDetail = detailOverride == null || detailOverride.isEmpty() ? detail : detailOverride;
        return new SoundingTrack(title, newDetail, file, false, newState);
    }

    public boolean isPresent() {
        return !title.isEmpty();
    }
}
