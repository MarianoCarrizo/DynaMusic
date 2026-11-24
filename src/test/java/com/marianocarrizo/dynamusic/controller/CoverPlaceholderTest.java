package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverPlaceholderTest {

    @Test
    void nothingIsShownWhileTheCoverIsLoading() {
        assertEquals("", CoverPlaceholder.caption(false, false, "Abbey Road"));
    }

    @Test
    void nothingIsShownWhenThereIsArt() {
        assertEquals("", CoverPlaceholder.caption(true, true, "Abbey Road"));
    }

    @Test
    void albumsWithoutArtShowTheirName() {
        assertEquals("Abbey Road", CoverPlaceholder.caption(true, false, "Abbey Road"));
    }

    @Test
    void blankNamesFallBackToAGenericCaption() {
        assertEquals("Unknown album", CoverPlaceholder.caption(true, false, "  "));
        assertEquals("Unknown album", CoverPlaceholder.caption(true, false, null));
    }
}
