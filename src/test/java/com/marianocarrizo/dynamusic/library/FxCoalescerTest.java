package com.marianocarrizo.dynamusic.library;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxCoalescerTest {

    private final Queue<Runnable> fxQueue = new ArrayDeque<>();
    private final List<Integer> delivered = new ArrayList<>();
    private final FxCoalescer<Integer> coalescer = new FxCoalescer<>(fxQueue::add, delivered::add);

    private void runFx() {
        while (!fxQueue.isEmpty()) fxQueue.poll().run();
    }

    @Test
    void manyPublishesScheduleOneUpdateWithTheLatestValue() {
        coalescer.publish(1);
        coalescer.publish(2);
        coalescer.publish(3);

        assertEquals(1, fxQueue.size());
        runFx();

        assertEquals(List.of(3), delivered);
    }

    @Test
    void publishingAfterADeliverySchedulesAgain() {
        coalescer.publish(1);
        runFx();
        coalescer.publish(2);
        runFx();

        assertEquals(List.of(1, 2), delivered);
    }

    @Test
    void clearedValueIsNeverDelivered() {
        coalescer.publish(1);
        coalescer.clear();
        runFx();

        assertTrue(delivered.isEmpty());
    }

    @Test
    void valuePublishedAfterClearIsDelivered() {
        coalescer.publish(1);
        coalescer.clear();
        coalescer.publish(2);
        runFx();

        assertEquals(List.of(2), delivered);
    }
}
