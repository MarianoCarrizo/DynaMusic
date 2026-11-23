package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.enums.TrackType;

public record PlaybackState(
        MusicService.PlaybackMode mode,
        boolean previewSoundedLast,
        TrackType moodType,
        SoundingTrack preview,
        SoundingTrack mood,
        SoundingTrack playlist,
        SoundingTrack onDemand) {
}
