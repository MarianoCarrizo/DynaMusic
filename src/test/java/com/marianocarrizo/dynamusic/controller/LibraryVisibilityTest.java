package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryVisibilityTest {

    @Test
    void aSensiblePositionIsKept() {
        assertEquals(0.45, LibraryVisibility.sanitize(0.45));
    }

    @Test
    void positionsOutsideTheUsableRangeFallBackToTheDefault() {
        assertEquals(LibraryVisibility.DEFAULT_DIVIDER, LibraryVisibility.sanitize(0.0));
        assertEquals(LibraryVisibility.DEFAULT_DIVIDER, LibraryVisibility.sanitize(1.0));
        assertEquals(LibraryVisibility.DEFAULT_DIVIDER, LibraryVisibility.sanitize(-3));
    }

    @Test
    void notANumberFallsBackToTheDefault() {
        assertEquals(LibraryVisibility.DEFAULT_DIVIDER, LibraryVisibility.sanitize(Double.NaN));
    }
}
