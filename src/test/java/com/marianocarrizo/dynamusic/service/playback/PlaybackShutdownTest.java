package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackShutdownTest {

    private final List<String> log = new ArrayList<>();
    private long clock;

    private FakePlayback player(String name, PlaybackStatus status) {
        FakePlayback playback = new FakePlayback(Runnable::run);
        playback.recordCalls(log, name);
        if (status == PlaybackStatus.PLAYING) playback.play();
        return playback;
    }

    private PlaybackShutdown shutdown(List<Playback> players) {
        return new PlaybackShutdown(() -> players, millis -> {
            log.add("sleep=" + millis);
            clock += millis * 1_000_000;
        }, () -> clock);
    }

    @Test
    void fadesOutThenDisposesThenWaitsForRelease() {
        shutdown(List.of(player("a", PlaybackStatus.PLAYING))).run();

        assertEquals(List.of("a.volume=0.0", "sleep=" + PlaybackShutdown.FADE_MILLIS, "a.dispose",
                "a.await<=" + PlaybackShutdown.RELEASE_BUDGET_MILLIS), log);
    }

    @Test
    void skipsTheFadeWhenNothingIsSounding() {
        shutdown(List.of(player("a", PlaybackStatus.READY))).run();

        assertEquals(List.of("a.dispose", "a.await<=" + PlaybackShutdown.RELEASE_BUDGET_MILLIS), log);
    }

    @Test
    void disposesEveryPlayerBeforeWaitingOnAny() {
        shutdown(List.of(player("a", PlaybackStatus.PLAYING), player("b", PlaybackStatus.READY))).run();

        assertTrue(log.indexOf("b.dispose") < log.indexOf("a.await<=" + PlaybackShutdown.RELEASE_BUDGET_MILLIS));
    }

    @Test
    void sharesOneBoundedBudgetAcrossStuckPlayers() {
        FakePlayback first = player("a", PlaybackStatus.READY);
        FakePlayback second = player("b", PlaybackStatus.READY);
        first.releaseTakes(false);
        second.releaseTakes(false);
        PlaybackShutdown shutdown = new PlaybackShutdown(() -> List.of(first, second), millis -> { },
                () -> clock += 300_000_000);

        shutdown.run();

        assertEquals(List.of("a.dispose", "b.dispose", "a.await<=200"), log);
    }

    @Test
    void doesNotWaitOnceTheBudgetIsSpent() {
        FakePlayback first = player("a", PlaybackStatus.READY);
        FakePlayback second = player("b", PlaybackStatus.READY);
        PlaybackShutdown shutdown = new PlaybackShutdown(() -> List.of(first, second), millis -> { },
                () -> clock += PlaybackShutdown.RELEASE_BUDGET_MILLIS * 1_000_000);

        shutdown.run();

        assertTrue(log.stream().noneMatch(entry -> entry.contains(".await")));
    }
}
