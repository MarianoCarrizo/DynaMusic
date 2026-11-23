package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.io.File;
import java.util.Optional;

public record TrackLabels(String title, String detail) {

    static final String DETAIL_SEPARATOR = " · ";

    public static TrackLabels of(File file, Optional<TrackTags> tags) {
        TrackTags known = tags.orElse(TrackTags.EMPTY);
        String title = known.title().isEmpty() ? TrackNames.title(file.getName()) : known.title();
        String album = known.album().isEmpty() ? TrackNames.album(file) : known.album();
        return new TrackLabels(title, detail(known.artist(), album));
    }

    static String detail(String artist, String album) {
        if (artist.isEmpty()) return album;
        if (album.isEmpty()) return artist;
        return artist + DETAIL_SEPARATOR + album;
    }
}
