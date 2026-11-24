package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameNormalizerTest {

    @Test
    void lowercasesAndStripsAccents() {
        assertEquals("cafe del mar", NameNormalizer.normalize("Café Del MAR"));
    }

    @Test
    void keepsPlainNamesUntouched() {
        assertEquals("track 01.mp3", NameNormalizer.normalize("track 01.mp3"));
    }
}
