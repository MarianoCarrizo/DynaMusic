package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.service.TrackNames;

import java.io.File;

record PlaylistRow(String title, String album) {

    static PlaylistRow of(PlaylistItem item) {
        File file = new File(item.getPath());
        String title = item.getTitle() != null && !item.getTitle().isBlank()
                ? item.getTitle() : TrackNames.title(file.getName());
        return new PlaylistRow(title, TrackNames.album(file));
    }

    static PlaylistRow pending(String text) {
        return new PlaylistRow(text, "");
    }

    boolean hasAlbum() {
        return !album.isEmpty();
    }
}
