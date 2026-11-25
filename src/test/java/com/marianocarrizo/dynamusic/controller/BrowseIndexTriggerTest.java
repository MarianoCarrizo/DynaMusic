package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowseIndexTriggerTest {

    private static final File FIRST = new File("music-a");
    private static final File SECOND = new File("music-b");

    private final BrowseIndexTrigger trigger = new BrowseIndexTrigger();

    @Test
    void foldersNeverStartTheIndex() {
        assertTrue(trigger.due(LibraryView.FOLDERS, FIRST).isEmpty());
    }

    @Test
    void nothingStartsWithoutALibraryFolder() {
        assertTrue(trigger.due(LibraryView.ALBUMS, null).isEmpty());
        assertTrue(trigger.due(LibraryView.ARTISTS, null).isEmpty());
    }

    @Test
    void firstAlbumsSelectionStartsTheIndex() {
        assertEquals(Optional.of(FIRST), trigger.due(LibraryView.ALBUMS, FIRST));
    }

    @Test
    void firstArtistsSelectionStartsTheIndex() {
        assertEquals(Optional.of(FIRST), trigger.due(LibraryView.ARTISTS, FIRST));
    }

    @Test
    void sameFolderIsNotIndexedTwice() {
        trigger.due(LibraryView.ALBUMS, FIRST);

        assertTrue(trigger.due(LibraryView.ALBUMS, FIRST).isEmpty());
        assertTrue(trigger.due(LibraryView.ARTISTS, FIRST).isEmpty());
    }

    @Test
    void leavingAndComingBackDoesNotRestartIt() {
        trigger.due(LibraryView.ALBUMS, FIRST);
        trigger.due(LibraryView.FOLDERS, FIRST);

        assertTrue(trigger.due(LibraryView.ALBUMS, FIRST).isEmpty());
    }

    @Test
    void folderChangeWhileBrowsingRestartsIt() {
        trigger.due(LibraryView.ALBUMS, FIRST);

        assertEquals(Optional.of(SECOND), trigger.due(LibraryView.ALBUMS, SECOND));
    }

    @Test
    void folderChangeWhileInFoldersWaitsForTheNextBrowse() {
        trigger.due(LibraryView.ALBUMS, FIRST);

        assertTrue(trigger.due(LibraryView.FOLDERS, SECOND).isEmpty());
        assertEquals(Optional.of(SECOND), trigger.due(LibraryView.ARTISTS, SECOND));
    }
}
