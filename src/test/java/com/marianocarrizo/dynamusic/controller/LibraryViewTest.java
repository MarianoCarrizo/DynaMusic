package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryViewTest {

    @Test
    void sessionAlwaysShowsFolders() {
        for (LibraryView selected : LibraryView.values()) {
            assertEquals(LibraryView.FOLDERS, LibraryView.effective(AppMode.SESSION, selected));
        }
    }

    @Test
    void listenShowsTheSelectedView() {
        for (LibraryView selected : LibraryView.values()) {
            assertEquals(selected, LibraryView.effective(AppMode.LISTEN, selected));
        }
    }

    @Test
    void onlyAlbumsAndArtistsBrowseTheIndex() {
        assertFalse(LibraryView.FOLDERS.browses());
        assertTrue(LibraryView.ALBUMS.browses());
        assertTrue(LibraryView.ARTISTS.browses());
    }

    @Test
    void foldersShowThePathAndTheOthersShowTheBrowseStatus() {
        assertEquals("C:/music", LibraryView.FOLDERS.status("C:/music", "12 albums"));
        assertEquals("12 albums", LibraryView.ALBUMS.status("C:/music", "12 albums"));
        assertEquals("3 artists", LibraryView.ARTISTS.status("C:/music", "3 artists"));
    }
}
