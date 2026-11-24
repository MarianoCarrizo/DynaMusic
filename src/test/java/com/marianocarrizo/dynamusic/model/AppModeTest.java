package com.marianocarrizo.dynamusic.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppModeTest {

    @Test
    void storedNamesResolveCaseInsensitively() {
        assertEquals(AppMode.LISTEN, AppMode.fromStored("listen"));
        assertEquals(AppMode.SESSION, AppMode.fromStored("SESSION"));
    }

    @Test
    void missingOrUnknownNamesResolveToSession() {
        assertEquals(AppMode.SESSION, AppMode.fromStored(null));
        assertEquals(AppMode.SESSION, AppMode.fromStored("party"));
    }

    @Test
    void labelsAreHumanReadable() {
        assertEquals("Listen", AppMode.LISTEN.label());
        assertEquals("Session", AppMode.SESSION.label());
    }
}
