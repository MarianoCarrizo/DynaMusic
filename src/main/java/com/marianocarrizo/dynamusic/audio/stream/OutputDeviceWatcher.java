package com.marianocarrizo.dynamusic.audio.stream;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class OutputDeviceWatcher {

    static final long POLL_MILLIS = 1000;
    private static final String THREAD_NAME = "output-device-watcher";

    private final DefaultOutputDevice device;
    private final Runnable onChange;
    private final ScheduledExecutorService scheduler;
    private final long periodMillis;
    private String lastId;
    private boolean started;

    OutputDeviceWatcher(DefaultOutputDevice device, Runnable onChange, ScheduledExecutorService scheduler,
            long periodMillis) {
        this.device = device;
        this.onChange = onChange;
        this.scheduler = scheduler;
        this.periodMillis = periodMillis;
    }

    public static OutputDeviceWatcher every(DefaultOutputDevice device, Runnable onChange) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        return new OutputDeviceWatcher(device, onChange, scheduler, POLL_MILLIS);
    }

    public synchronized void start() {
        if (started || scheduler.isShutdown()) return;
        started = true;
        scheduler.scheduleWithFixedDelay(this::safePoll, 0, periodMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        started = false;
        scheduler.shutdownNow();
    }

    synchronized void poll() {
        Optional<String> reading = device.currentId();
        if (reading.isEmpty()) return;
        String previous = lastId;
        lastId = reading.get();
        if (previous != null && !previous.equals(lastId)) onChange.run();
    }

    private void safePoll() {
        try {
            poll();
        } catch (RuntimeException failure) {
            System.err.println("[output-device] poll failed: " + failure);
        }
    }
}
