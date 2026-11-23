package com.marianocarrizo.dynamusic.service;

import java.io.File;

public record SoundingTrack(String title, String detail, File file, boolean playing) {

    public static final SoundingTrack ABSENT = new SoundingTrack("", "", null, false);

    public SoundingTrack(String title, String detail, boolean playing) {
        this(title, detail, null, playing);
    }

    public static SoundingTrack of(File file, boolean playing) {
        return new SoundingTrack(TrackNames.title(file.getName()), TrackNames.album(file), file, playing);
    }

    public static SoundingTrack of(String title, File file, boolean playing) {
        return new SoundingTrack(title, TrackNames.album(file), file, playing);
    }

    public boolean isPresent() {
        return !title.isEmpty();
    }
}
