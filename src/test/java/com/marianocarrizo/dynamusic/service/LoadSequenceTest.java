package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadSequenceTest {

    @Test
    void theLatestBeginIsCurrent() {
        LoadSequence sequence = new LoadSequence();

        assertTrue(sequence.isCurrent(sequence.begin()));
    }

    @Test
    void aNewerBeginSupersedesTheOlderOne() {
        LoadSequence sequence = new LoadSequence();
        int older = sequence.begin();
        int newer = sequence.begin();

        assertFalse(sequence.isCurrent(older));
        assertTrue(sequence.isCurrent(newer));
    }

    @Test
    void invalidatingCancelsThePendingLoad() {
        LoadSequence sequence = new LoadSequence();
        int pending = sequence.begin();

        sequence.invalidate();

        assertFalse(sequence.isCurrent(pending));
    }

    @Test
    void aLoadStartedAfterInvalidatingIsCurrent() {
        LoadSequence sequence = new LoadSequence();
        sequence.begin();
        sequence.invalidate();

        assertTrue(sequence.isCurrent(sequence.begin()));
    }
}
