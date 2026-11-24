package com.marianocarrizo.dynamusic.library;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishGateTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void dueAfterEnoughItems() {
        PublishGate gate = new PublishGate(500, 1_000, now::get);

        assertFalse(gate.due(499));
        assertTrue(gate.due(500));
        assertFalse(gate.due(999));
        assertTrue(gate.due(1000));
    }

    @Test
    void dueAfterEnoughTimeEvenWithFewItems() {
        PublishGate gate = new PublishGate(500, 1_000, now::get);

        assertFalse(gate.due(1));
        now.set(1_000);
        assertTrue(gate.due(2));
        assertFalse(gate.due(3));
    }
}
