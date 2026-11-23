package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.service.TrackLabels;
import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.io.File;
import java.util.Optional;

record PlaylistRow(String title, String album) {

    static PlaylistRow of(PlaylistItem item, Optional<TrackTags> tags) {
        TrackLabels labels = TrackLabels.of(new File(item.getPath()), tags);
        boolean hasStoredTitle = item.getTitle() != null && !item.getTitle().isBlank();
        return new PlaylistRow(hasStoredTitle ? item.getTitle() : labels.title(), labels.detail());
    }

    static PlaylistRow pending(String text) {
        return new PlaylistRow(text, "");
    }

    boolean hasAlbum() {
        return !album.isEmpty();
    }
}
