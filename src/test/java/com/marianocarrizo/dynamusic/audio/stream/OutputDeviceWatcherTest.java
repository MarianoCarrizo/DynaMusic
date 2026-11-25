package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutputDeviceWatcherTest {

    private static final long FAST_MILLIS = 1;
    private static final long AWAIT_SECONDS = 5;

    private final Queue<Optional<String>> readings = new ArrayDeque<>();
    private final AtomicInteger changes = new AtomicInteger();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private OutputDeviceWatcher watcher(DefaultOutputDevice device) {
        return new OutputDeviceWatcher(device, changes::incrementAndGet, scheduler, FAST_MILLIS);
    }

    private OutputDeviceWatcher scripted() {
        return watcher(() -> readings.isEmpty() ? Optional.empty() : readings.remove());
    }

    private void feed(String... ids) {
        for (String id : ids) readings.add(id == null ? Optional.empty() : Optional.of(id));
    }

    private void pollTimes(OutputDeviceWatcher watcher, int times) {
        for (int i = 0; i < times; i++) watcher.poll();
    }

    @Test
    void firstReadingOnlyRemembersTheDevice() {
        feed("speakers");
        pollTimes(scripted(), 1);
        assertEquals(0, changes.get());
    }

    @Test
    void sameIdDoesNotFire() {
        feed("speakers", "speakers", "speakers");
        pollTimes(scripted(), 3);
        assertEquals(0, changes.get());
    }

    @Test
    void changedIdFiresOncePerChange() {
        feed("speakers", "headphones", "headphones", "speakers");
        pollTimes(scripted(), 4);
        assertEquals(2, changes.get());
    }

    @Test
    void emptyReadingsNeitherFireNorForgetTheLastDevice() {
        feed("speakers", null, null, "speakers", null, "headphones");
        pollTimes(scripted(), 6);
        assertEquals(1, changes.get());
    }

    @Test
    void startingWithoutAKnownDeviceFiresOnlyAfterTwoKnownReadings() {
        feed(null, "speakers", "headphones");
        pollTimes(scripted(), 3);
        assertEquals(1, changes.get());
    }

    @Test
    void startPollsInTheBackgroundAndStopHaltsIt() throws InterruptedException {
        CountDownLatch warm = new CountDownLatch(3);
        AtomicInteger reads = new AtomicInteger();
        OutputDeviceWatcher watcher = watcher(() -> {
            reads.incrementAndGet();
            warm.countDown();
            return Optional.of("speakers");
        });
        watcher.start();
        assertTrue(warm.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        watcher.stop();
        assertTrue(scheduler.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS));
        int atStop = reads.get();
        watcher.stop();
        assertEquals(atStop, reads.get());
        assertEquals(0, changes.get());
    }

    @Test
    void aFailingCallbackDoesNotStopThePolling() throws InterruptedException {
        CountDownLatch polled = new CountDownLatch(4);
        AtomicInteger counter = new AtomicInteger();
        OutputDeviceWatcher watcher = new OutputDeviceWatcher(() -> {
            polled.countDown();
            return Optional.of("device-" + counter.getAndIncrement());
        }, () -> {
            throw new IllegalStateException("callback failed");
        }, scheduler, FAST_MILLIS);
        watcher.start();
        assertTrue(polled.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        watcher.stop();
    }

    @Test
    void unavailableDeviceNeverFires() {
        OutputDeviceWatcher watcher = watcher(DefaultOutputDevice.unavailable());
        pollTimes(watcher, 3);
        assertEquals(0, changes.get());
    }
}
