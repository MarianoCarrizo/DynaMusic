package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;

public record PendingLoad(NowPlayingSource source, TrackType moodType, SoundingTrack track) {
}
