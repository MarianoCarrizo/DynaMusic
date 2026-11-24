package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.PlayQueue;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueReorderTest {

    private static final File A = new File("a.flac");
    private static final File B = new File("b.flac");
    private static final File C = new File("c.flac");
    private static final File D = new File("d.flac");

    private final PlayQueue queue = new PlayQueue();

    private void dropRow(int from, int slot) {
        queue.move(from, QueueReorder.targetIndex(from, slot));
    }

    @Test
    void droppingBelowTheRowMovesItDownToTheSlotBefore() {
        queue.add(List.of(A, B, C, D));

        dropRow(0, 3);

        assertEquals(List.of(B, C, A, D), queue.items());
    }

    @Test
    void droppingAboveTheRowMovesItUpToTheSlot() {
        queue.add(List.of(A, B, C, D));

        dropRow(3, 1);

        assertEquals(List.of(A, D, B, C), queue.items());
    }

    @Test
    void droppingOnItsOwnSlotsChangesNothing() {
        queue.add(List.of(A, B, C));

        dropRow(1, 1);
        dropRow(1, 2);

        assertEquals(List.of(A, B, C), queue.items());
    }

    @Test
    void droppingAtTheEndMovesItToTheLastPosition() {
        queue.add(List.of(A, B, C));

        dropRow(0, 3);

        assertEquals(List.of(B, C, A), queue.items());
    }

    @Test
    void removedRowNeverPlays() {
        queue.add(List.of(A, B, C));

        queue.remove(1);

        assertEquals(A, queue.poll().orElseThrow());
        assertEquals(C, queue.poll().orElseThrow());
        assertTrue(queue.isEmpty());
    }

    @Test
    void doubleClickMovesTheRowToTheFrontSoItPlaysNext() {
        queue.add(List.of(A, B, C));

        queue.moveToFront(2);

        assertEquals(C, queue.poll().orElseThrow());
        assertEquals(List.of(A, B), queue.items());
    }
}
