package com.marianocarrizo.dynamusic.library;

import java.util.List;

public record Album(AlbumKey key, String title, String artist, String year, List<TrackEntry> tracks,
                    String coverTrack) {

    public boolean hasCoverTrack() {
        return !coverTrack.isEmpty();
    }
}
