package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.tags.TrackTags;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistRowTest {

    @Test
    void rowTitleDropsTheExtensionAndKeepsTheTrackNumber() {
        PlaylistRow row = PlaylistRow.of(new PlaylistItem("music/Mega Man X/04 Opening Stage X.flac"), Optional.empty());

        assertEquals("04 Opening Stage X", row.title());
    }

    @Test
    void rowShowsTheParentFolderAsAlbum() {
        PlaylistRow row = PlaylistRow.of(new PlaylistItem("music/Mega Man X/04 Opening Stage X.flac"), Optional.empty());

        assertEquals("Mega Man X", row.album());
        assertTrue(row.hasAlbum());
    }

    @Test
    void sameFileNameFromTwoAlbumsStaysDistinguishable() {
        PlaylistRow first = PlaylistRow.of(new PlaylistItem("music/Mega Man X/04 Opening Stage X.flac"), Optional.empty());
        PlaylistRow second = PlaylistRow.of(new PlaylistItem("music/Mega Man X2/04 Opening Stage X.flac"), Optional.empty());

        assertEquals(first.title(), second.title());
        assertFalse(first.album().equals(second.album()));
    }

    @Test
    void aStoredTitleWinsOverTheFileName() {
        PlaylistItem item = new PlaylistItem("music/cache/abc123.mp3", "https://youtu.be/abc123", "Boss Theme");

        assertEquals("Boss Theme", PlaylistRow.of(item, Optional.empty()).title());
    }

    @Test
    void aBlankStoredTitleFallsBackToTheFileName() {
        PlaylistItem item = new PlaylistItem("music/Album/Song.mp3", null, "  ");

        assertEquals("Song", PlaylistRow.of(item, Optional.empty()).title());
    }

    @Test
    void tagsFillTheTitleAndTheArtistAlbumDetail() {
        TrackTags tags = new TrackTags("Opening Stage", "Band", "Mega Man X", 4, "1993", false);

        PlaylistRow row = PlaylistRow.of(new PlaylistItem("music/x/04.flac"), Optional.of(tags));

        assertEquals("Opening Stage", row.title());
        assertEquals("Band \u00B7 Mega Man X", row.album());
    }

    @Test
    void aStoredTitleStillWinsOverTheTagTitle() {
        TrackTags tags = new TrackTags("Tag Title", "", "", 0, "", false);
        PlaylistItem item = new PlaylistItem("music/cache/abc.mp3", "https://youtu.be/abc", "Stored");

        assertEquals("Stored", PlaylistRow.of(item, Optional.of(tags)).title());
    }

    @Test
    void pendingRowHasNoAlbum() {
        PlaylistRow row = PlaylistRow.pending("Downloading...");

        assertEquals("Downloading...", row.title());
        assertFalse(row.hasAlbum());
    }

    @Test
    void onlyTheCurrentRowShowsAMarker() {
        assertEquals(RowPlayback.NONE, RowPlayback.of(false, true));
        assertEquals(RowPlayback.PLAYING, RowPlayback.of(true, true));
        assertEquals(RowPlayback.PAUSED, RowPlayback.of(true, false));
        assertEquals("", RowPlayback.NONE.marker());
        assertFalse(RowPlayback.NONE.isCurrent());
        assertTrue(RowPlayback.PAUSED.isCurrent());
    }
}
