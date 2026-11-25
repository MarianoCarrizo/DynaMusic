package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.service.CoverThumbnails;

import java.io.File;
import java.util.function.BooleanSupplier;

final class AlbumCovers {

    private AlbumCovers() {
    }

    static void show(CoverThumbnails covers, CoverBox target, Album album, BooleanSupplier stillBound) {
        target.loading();
        String key = album.key().folder() + "\n" + album.key().title();
        File coverTrack = album.hasCoverTrack() ? new File(album.coverTrack()) : null;
        covers.load(key, coverTrack, new File(album.key().folder()), stillBound, image -> {
            if (stillBound.getAsBoolean()) target.resolved(image, album.title());
        });
    }
}
