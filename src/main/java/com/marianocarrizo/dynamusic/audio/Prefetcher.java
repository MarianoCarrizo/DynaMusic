package com.marianocarrizo.dynamusic.audio;

import java.io.File;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class Prefetcher {

    private static final String WORKER_NAME = "audio-prefetch";

    private static final class Request {
        private final File file;
        private boolean ready;

        private Request(File file) {
            this.file = file;
        }
    }

    private final AudioPrep prep;
    private final Executor worker;
    private Request pending;
    private boolean running;

    public Prefetcher(AudioPrep prep) {
        this(prep, backgroundWorker());
    }

    Prefetcher(AudioPrep prep, Executor worker) {
        this.prep = prep;
        this.worker = worker;
    }

    public void schedule(File next, CompletionStage<?> afterCurrent) {
        Request request = new Request(next);
        synchronized (this) {
            pending = request;
        }
        afterCurrent.whenComplete((result, error) -> {
            synchronized (this) {
                request.ready = true;
            }
            startNext();
        });
    }

    public synchronized void cancel() {
        pending = null;
    }

    private void startNext() {
        File file;
        synchronized (this) {
            if (running || pending == null || !pending.ready) return;
            file = pending.file;
            pending = null;
            running = true;
        }
        if (!PlaybackSupport.isPlayable(file)) {
            finished();
            return;
        }
        try {
            worker.execute(() -> prefetch(file));
        } catch (RuntimeException rejected) {
            finished();
        }
    }

    private void prefetch(File file) {
        try {
            prep.prepare(file, Runnable::run).join();
        } catch (RuntimeException silentFailure) {
            System.err.println("[prefetch] skipped " + file.getName() + ": " + silentFailure.getMessage());
        } finally {
            finished();
        }
    }

    private void finished() {
        synchronized (this) {
            running = false;
        }
        startNext();
    }

    private static Executor backgroundWorker() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, WORKER_NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }
}
