package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.library.AlbumKey;
import com.marianocarrizo.dynamusic.library.Artist;
import com.marianocarrizo.dynamusic.library.LibrarySnapshot;
import com.marianocarrizo.dynamusic.library.TrackEntry;
import com.marianocarrizo.dynamusic.tags.TrackTags;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowseNavigatorTest {

    private static Album album(String title, int tracks) {
        List<TrackEntry> entries = new ArrayList<>();
        for (int i = 0; i < tracks; i++) {
            entries.add(new TrackEntry("/m/" + title + "/" + i + ".flac", 1, 1, TrackTags.EMPTY));
        }
        return new Album(new AlbumKey(title.toLowerCase(), "/m/" + title), title, "Ana", "", entries, "");
    }

    private static LibrarySnapshot snapshot(Album... albums) {
        return new LibrarySnapshot(List.of(albums), List.of(new Artist("Ana", List.of(albums))), 0);
    }

    private final BrowseNavigator navigator = new BrowseNavigator();

    @Test
    void startsOnTheAlbumGrid() {
        assertEquals(BrowseNavigator.Page.ALBUMS, navigator.page());
        assertFalse(navigator.canGoBack());
    }

    @Test
    void switchingSectionShowsItsRoot() {
        navigator.showSection(BrowseSection.ARTISTS);

        assertEquals(BrowseNavigator.Page.ARTISTS, navigator.page());
        navigator.showSection(BrowseSection.ALBUMS);
        assertEquals(BrowseNavigator.Page.ALBUMS, navigator.page());
    }

    @Test
    void openingAnAlbumShowsItsDetailAndBackReturns() {
        Album blue = album("Blue", 2);

        navigator.openAlbum(blue);

        assertEquals(BrowseNavigator.Page.ALBUM_DETAIL, navigator.page());
        assertEquals(blue, navigator.album());
        assertTrue(navigator.canGoBack());
        navigator.back();
        assertEquals(BrowseNavigator.Page.ALBUMS, navigator.page());
        assertNull(navigator.album());
    }

    @Test
    void albumOpenedFromAnArtistGoesBackToThatArtist() {
        Album blue = album("Blue", 2);
        Artist ana = new Artist("Ana", List.of(blue));
        navigator.showSection(BrowseSection.ARTISTS);

        navigator.openArtist(ana);
        assertEquals(BrowseNavigator.Page.ARTIST_ALBUMS, navigator.page());
        assertEquals(ana, navigator.artist());
        navigator.openAlbum(blue);
        navigator.back();

        assertEquals(BrowseNavigator.Page.ARTIST_ALBUMS, navigator.page());
        navigator.back();
        assertEquals(BrowseNavigator.Page.ARTISTS, navigator.page());
    }

    @Test
    void backOnTheRootDoesNothing() {
        navigator.back();

        assertEquals(BrowseNavigator.Page.ALBUMS, navigator.page());
    }

    @Test
    void switchingSectionDropsTheOpenAlbum() {
        navigator.openAlbum(album("Blue", 2));

        navigator.showSection(BrowseSection.ARTISTS);

        assertEquals(BrowseNavigator.Page.ARTISTS, navigator.page());
        assertFalse(navigator.canGoBack());
    }

    @Test
    void refreshKeepsTheOpenAlbumAndSwapsInTheNewTracks() {
        navigator.openAlbum(album("Blue", 2));
        Album grown = album("Blue", 3);

        navigator.refresh(snapshot(grown));

        assertEquals(BrowseNavigator.Page.ALBUM_DETAIL, navigator.page());
        assertEquals(3, navigator.album().tracks().size());
    }

    @Test
    void refreshClosesAnAlbumThatNoLongerExists() {
        navigator.openAlbum(album("Blue", 2));

        navigator.refresh(snapshot(album("Red", 1)));

        assertEquals(BrowseNavigator.Page.ALBUMS, navigator.page());
    }

    @Test
    void refreshClosesAnArtistThatNoLongerExists() {
        navigator.showSection(BrowseSection.ARTISTS);
        navigator.openArtist(new Artist("Gone", List.of()));

        navigator.refresh(snapshot(album("Red", 1)));

        assertEquals(BrowseNavigator.Page.ARTISTS, navigator.page());
    }
}
