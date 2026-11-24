package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryFilterTest {

    @Test
    void emptyQueryMatchesEverything() {
        LibraryFilter filter = new LibraryFilter("");
        assertTrue(filter.isEmpty());
        assertTrue(filter.matches("anything"));
    }

    @Test
    void blankQueryIsTreatedAsEmpty() {
        assertTrue(new LibraryFilter("   ").isEmpty());
    }

    @Test
    void matchesSubstringsIgnoringCase() {
        LibraryFilter filter = new LibraryFilter("red hot");
        assertTrue(filter.matches("Red Hot Chili Peppers 24bit"));
        assertTrue(filter.matches("The RED HOT tracks"));
        assertFalse(filter.matches("Blue Cold"));
    }

    @Test
    void ignoresAccentsOnBothSides() {
        assertTrue(new LibraryFilter("cafe").matches("Café Tacvba"));
        assertTrue(new LibraryFilter("café").matches("cafe.mp3"));
    }

    @Test
    void noneMatchesEverything() {
        assertTrue(LibraryFilter.NONE.matches("whatever"));
    }
}
