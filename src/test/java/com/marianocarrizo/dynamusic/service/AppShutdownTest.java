package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppShutdownTest {

    private final List<String> log = new ArrayList<>();

    @Test
    void runsTheStepsInOrder() {
        new AppShutdown(List.of(() -> log.add("stop"), () -> log.add("save"), () -> log.add("exit"))).run();

        assertEquals(List.of("stop", "save", "exit"), log);
    }

    @Test
    void aFailingStepDoesNotPreventTheExit() {
        new AppShutdown(List.of(() -> {
            throw new IllegalStateException("device");
        }, () -> log.add("exit"))).run();

        assertEquals(List.of("exit"), log);
    }

    @Test
    void runsOnlyOnce() {
        AppShutdown shutdown = new AppShutdown(List.of(() -> log.add("exit")));

        shutdown.run();
        shutdown.run();

        assertEquals(List.of("exit"), log);
    }
}
