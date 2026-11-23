package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.enums.TrackType;

import java.util.Optional;

public record PlaybackState(
        MusicService.PlaybackMode mode,
        boolean previewSoundedLast,
        TrackType moodType,
        SoundingTrack preview,
        SoundingTrack mood,
        SoundingTrack playlist,
        SoundingTrack onDemand,
        Optional<PendingLoad> pending) {

    public PlaybackState(MusicService.PlaybackMode mode, boolean previewSoundedLast, TrackType moodType,
                         SoundingTrack preview, SoundingTrack mood, SoundingTrack playlist,
                         SoundingTrack onDemand) {
        this(mode, previewSoundedLast, moodType, preview, mood, playlist, onDemand, Optional.empty());
    }
}
