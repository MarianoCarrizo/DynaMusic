package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowseStateTest {

    @Test
    void noFolderAsksForOne() {
        assertEquals(BrowseState.NO_FOLDER, BrowseState.of(false, false, 0));
        assertEquals(BrowseState.NO_FOLDER, BrowseState.of(false, true, 10));
    }

    @Test
    void emptyLibraryWhileIndexingIsIndexing() {
        assertEquals(BrowseState.INDEXING, BrowseState.of(true, true, 0));
    }

    @Test
    void emptyLibraryAfterIndexingIsEmpty() {
        assertEquals(BrowseState.EMPTY, BrowseState.of(true, false, 0));
    }

    @Test
    void tracksMeanReadyEvenWhileIndexing() {
        assertEquals(BrowseState.READY, BrowseState.of(true, true, 5));
        assertEquals(BrowseState.READY, BrowseState.of(true, false, 5));
    }

    @Test
    void onlyReadyHidesThePlaceholder() {
        assertFalse(BrowseState.READY.showsPlaceholder());
        assertTrue(BrowseState.NO_FOLDER.showsPlaceholder());
        assertTrue(BrowseState.INDEXING.showsPlaceholder());
        assertTrue(BrowseState.EMPTY.showsPlaceholder());
    }

    @Test
    void placeholdersHaveText() {
        for (BrowseState state : BrowseState.values()) {
            assertEquals(state.showsPlaceholder(), !state.message().isEmpty());
        }
    }
}
