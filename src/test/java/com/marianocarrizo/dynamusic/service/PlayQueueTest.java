package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayQueueTest {

    private static final File A = new File("a.flac");
    private static final File B = new File("b.flac");
    private static final File C = new File("c.flac");
    private static final File D = new File("d.flac");

    private final PlayQueue queue = new PlayQueue();

    @Test
    void playNextPutsTheTrackAtTheFront() {
        queue.add(List.of(A));

        queue.playNext(List.of(B));

        assertEquals(List.of(B, A), queue.items());
    }

    @Test
    void playNextKeepsTheOrderOfAGroup() {
        queue.add(List.of(A));

        queue.playNext(List.of(B, C));

        assertEquals(List.of(B, C, A), queue.items());
    }

    @Test
    void addAppendsToTheEnd() {
        queue.add(List.of(A));

        queue.add(List.of(C));

        assertEquals(List.of(A, C), queue.items());
    }

    @Test
    void removedTracksNeverComeOut() {
        queue.add(List.of(A, B, C));

        queue.remove(1);

        assertEquals(Optional.of(A), queue.poll());
        assertEquals(Optional.of(C), queue.poll());
        assertEquals(Optional.empty(), queue.poll());
    }

    @Test
    void removingOutOfRangeIsIgnored() {
        queue.add(List.of(A));

        queue.remove(-1);
        queue.remove(1);

        assertEquals(List.of(A), queue.items());
    }

    @Test
    void moveRelocatesATrackToTheTargetIndex() {
        queue.add(List.of(A, B, C, D));

        queue.move(0, 2);
        assertEquals(List.of(B, C, A, D), queue.items());

        queue.move(3, 0);
        assertEquals(List.of(D, B, C, A), queue.items());
    }

    @Test
    void moveToFrontPromotesATrack() {
        queue.add(List.of(A, B, C));

        queue.moveToFront(2);

        assertEquals(List.of(C, A, B), queue.items());
    }

    @Test
    void peekDoesNotConsume() {
        queue.add(List.of(A));

        assertEquals(Optional.of(A), queue.peek());
        assertEquals(1, queue.size());
    }

    @Test
    void pollConsumesInOrder() {
        queue.add(List.of(A, B));

        assertEquals(Optional.of(A), queue.poll());
        assertEquals(List.of(B), queue.items());
    }

    @Test
    void clearEmptiesTheQueue() {
        queue.add(List.of(A, B));

        queue.clear();

        assertTrue(queue.isEmpty());
    }

    @Test
    void listenersHearEveryRealChangeAndNothingElse() {
        AtomicInteger changes = new AtomicInteger();
        queue.addListener(changes::incrementAndGet);

        queue.add(List.of(A, B));
        queue.playNext(List.of(C));
        queue.move(0, 1);
        queue.moveToFront(2);
        queue.remove(0);
        queue.poll();
        queue.clear();
        assertEquals(7, changes.get());

        queue.clear();
        queue.poll();
        queue.remove(0);
        queue.add(List.of());
        queue.move(0, 0);
        assertEquals(7, changes.get());
    }

    @Test
    void itemsAreAReadOnlyView() {
        queue.add(List.of(A));

        assertThrows(UnsupportedOperationException.class, () -> queue.items().clear());
    }
}
