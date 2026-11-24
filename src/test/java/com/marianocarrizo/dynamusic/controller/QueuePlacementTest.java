package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.PlayQueue;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueuePlacementTest {

    private static final File A = new File("a.flac");
    private static final File B = new File("b.flac");
    private static final File C = new File("c.flac");

    @Test
    void nextInsertsAtTheFront() {
        PlayQueue queue = new PlayQueue();
        queue.add(List.of(A));

        QueuePlacement.NEXT.apply(queue, List.of(B));

        assertEquals(List.of(B, A), queue.items());
    }

    @Test
    void endAppends() {
        PlayQueue queue = new PlayQueue();
        queue.add(List.of(A));

        QueuePlacement.END.apply(queue, List.of(B, C));

        assertEquals(List.of(A, B, C), queue.items());
    }
}
