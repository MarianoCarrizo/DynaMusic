package com.marianocarrizo.dynamusic.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackTagStoreTest {

    @TempDir Path dir;

    private final Queue<Runnable> workerQueue = new ArrayDeque<>();
    private final Executor queuedWorker = workerQueue::add;
    private final AtomicInteger notifications = new AtomicInteger();
    private final AtomicInteger reads = new AtomicInteger();

    private TrackTagStore store() {
        TrackTagStore store = new TrackTagStore(file -> TagFormat.FLAC, queuedWorker, Runnable::run);
        store.onLoaded(notifications::incrementAndGet);
        return store;
    }

    private File flac(String name, String title) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, TagFixtures.taggedFlac("TITLE=" + title));
        return file.toFile();
    }

    private void runWorker() {
        while (!workerQueue.isEmpty()) workerQueue.poll().run();
    }

    @Test
    void peekIsEmptyUntilTheWorkerRuns() throws IOException {
        TrackTagStore store = store();
        File file = flac("a.flac", "First");

        store.request(file);

        assertTrue(store.peek(file).isEmpty());
        runWorker();
        assertEquals("First", store.peek(file).orElseThrow().title());
    }

    @Test
    void listenersAreNotifiedOncePerBatch() throws IOException {
        TrackTagStore store = store();

        store.request(List.of(flac("a.flac", "A"), flac("b.flac", "B")));
        runWorker();

        assertEquals(1, notifications.get());
    }

    @Test
    void unchangedFilesAreNotReloadedOrNotified() throws IOException {
        TrackTagStore store = new TrackTagStore(file -> {
            reads.incrementAndGet();
            return TagFormat.FLAC;
        }, queuedWorker, Runnable::run);
        store.onLoaded(notifications::incrementAndGet);
        File file = flac("a.flac", "A");

        store.request(file);
        runWorker();
        store.request(file);
        runWorker();

        assertEquals(1, reads.get());
        assertEquals(1, notifications.get());
    }

    @Test
    void duplicateRequestsWhilePendingQueueOnlyOneJob() throws IOException {
        TrackTagStore store = store();
        File file = flac("a.flac", "A");

        store.request(file);
        store.request(file);

        assertEquals(1, workerQueue.size());
    }

    @Test
    void modifiedFilesAreReloaded() throws IOException {
        TrackTagStore store = store();
        File file = flac("a.flac", "Old");
        store.request(file);
        runWorker();

        Files.write(file.toPath(), TagFixtures.taggedFlac("TITLE=Newer title"));
        file.setLastModified(file.lastModified() + 5000);
        store.request(file);
        runWorker();

        assertEquals("Newer title", store.peek(file).orElseThrow().title());
    }

    @Test
    void oldestEntriesAreEvictedBeyondCapacity() throws IOException {
        TrackTagStore store = store();
        File first = flac("first.flac", "First");
        store.request(first);
        runWorker();
        for (int i = 0; i < TrackTagStore.CAPACITY; i++) {
            File extra = new File(dir.toFile(), "missing-" + i + ".flac");
            store.request(extra);
        }
        runWorker();

        assertTrue(store.peek(first).isEmpty());
    }
}
