package com.marianocarrizo.dynamusic.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

public final class AppShutdown {

    private final List<Runnable> steps;
    private final AtomicBoolean started = new AtomicBoolean();

    public AppShutdown(List<Runnable> steps) {
        this.steps = List.copyOf(steps);
    }

    public static void exit() {
        Platform.exit();
        System.exit(0);
    }

    public void run() {
        if (!started.compareAndSet(false, true)) return;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException failure) {
                System.err.println("[shutdown] step failed: " + failure);
            }
        }
    }
}
