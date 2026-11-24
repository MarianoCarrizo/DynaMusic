package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeAheadMatcherTest {

    private static final long TIMEOUT = 1000;
    private static final List<String> NAMES = List.of("Alpha", "Beta", "Bravo", "Burst", "Charlie", "Café");

    private long now;
    private TypeAheadMatcher matcher;

    @BeforeEach
    void setUp() {
        now = 0;
        matcher = new TypeAheadMatcher(() -> now, TIMEOUT);
    }

    @Test
    void firstLetterSelectsNextItemAfterCurrentSelection() {
        assertEquals(1, matcher.next(NAMES, 0, 'b'));
    }

    @Test
    void searchStartsAfterTheCurrentItem() {
        assertEquals(2, matcher.next(NAMES, 1, 'b'));
    }

    @Test
    void searchWrapsAroundTheEnd() {
        assertEquals(0, matcher.next(NAMES, 4, 'a'));
    }

    @Test
    void unknownLetterFindsNothing() {
        assertEquals(TypeAheadMatcher.NOT_FOUND, matcher.next(NAMES, 0, 'z'));
    }

    @Test
    void noSelectionStartsFromTheTop() {
        assertEquals(0, matcher.next(NAMES, -1, 'a'));
    }

    @Test
    void repeatingTheSameLetterCyclesThroughItems() {
        assertEquals(1, matcher.next(NAMES, 0, 'b'));
        now += 100;
        assertEquals(2, matcher.next(NAMES, 1, 'b'));
        now += 100;
        assertEquals(3, matcher.next(NAMES, 2, 'b'));
        now += 100;
        assertEquals(1, matcher.next(NAMES, 3, 'b'));
    }

    @Test
    void lettersTypedWithinTheTimeoutBuildAPrefix() {
        assertEquals(1, matcher.next(NAMES, 0, 'b'));
        now += 500;
        assertEquals(2, matcher.next(NAMES, 1, 'r'));
        now += 500;
        assertEquals(2, matcher.next(NAMES, 2, 'a'));
    }

    @Test
    void prefixStaysOnTheCurrentItemWhenItStillMatches() {
        assertEquals(3, matcher.next(NAMES, 2, 'b'));
        now += 200;
        assertEquals(3, matcher.next(NAMES, 3, 'u'));
    }

    @Test
    void prefixResetsAfterTheTimeout() {
        assertEquals(1, matcher.next(NAMES, 0, 'b'));
        now += TIMEOUT + 1;
        assertEquals(4, matcher.next(NAMES, 1, 'c'));
    }

    @Test
    void failedLongerPrefixKeepsTheSelectionUnchanged() {
        assertEquals(1, matcher.next(NAMES, 0, 'b'));
        now += 100;
        assertEquals(TypeAheadMatcher.NOT_FOUND, matcher.next(NAMES, 1, 'x'));
    }

    @Test
    void ignoresCaseAndAccents() {
        assertEquals(4, matcher.next(NAMES, 0, 'C'));
        now += TIMEOUT + 1;
        assertEquals(1, matcher.next(List.of("Alpha", "Éclair"), 0, 'e'));
    }

    @Test
    void emptyListFindsNothing() {
        assertEquals(TypeAheadMatcher.NOT_FOUND, matcher.next(List.of(), -1, 'a'));
    }

    @Test
    void pendingReflectsTheTimeout() {
        assertFalse(matcher.hasPending());
        matcher.next(NAMES, 0, 'b');
        assertTrue(matcher.hasPending());
        now += TIMEOUT + 1;
        assertFalse(matcher.hasPending());
    }

    @Test
    void spaceExtendsAPrefix() {
        List<String> names = List.of("Red Hot", "Redd", "Blue");
        assertEquals(0, matcher.next(names, -1, 'r'));
        now += 100;
        assertEquals(0, matcher.next(names, 0, 'e'));
        now += 100;
        assertEquals(0, matcher.next(names, 0, 'd'));
        now += 100;
        assertEquals(0, matcher.next(names, 0, ' '));
    }
}
