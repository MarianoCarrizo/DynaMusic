package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.service.UpdateChecker.Version;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateOfferTest {

    private static final Version V2_0_0 = new Version(2, 0, 0);
    private static final Version V2_1_0 = new Version(2, 1, 0);
    private static final Version V1_9_0 = new Version(1, 9, 0);

    @Test
    void anUpdateIsOfferedWhenNothingWasSkipped() {
        assertTrue(UpdateOffer.isOffered(V2_0_0, Optional.empty()));
    }

    @Test
    void theSkippedVersionIsNotOfferedAgain() {
        assertFalse(UpdateOffer.isOffered(V2_0_0, Optional.of(V2_0_0)));
    }

    @Test
    void anOlderVersionThanTheSkippedOneIsNotOffered() {
        assertFalse(UpdateOffer.isOffered(V1_9_0, Optional.of(V2_0_0)));
    }

    @Test
    void aNewerVersionThanTheSkippedOneIsOffered() {
        assertTrue(UpdateOffer.isOffered(V2_1_0, Optional.of(V2_0_0)));
    }

    @Test
    void skippedTextIsParsedAndGarbageMeansNothingSkipped() {
        assertFalse(UpdateOffer.isOffered(V2_0_0, UpdateOffer.skipped("2.0.0")));
        assertTrue(UpdateOffer.isOffered(V2_0_0, UpdateOffer.skipped("garbage")));
        assertTrue(UpdateOffer.isOffered(V2_0_0, UpdateOffer.skipped(null)));
    }
}
