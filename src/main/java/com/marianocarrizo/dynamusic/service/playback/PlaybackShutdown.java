package com.marianocarrizo.dynamusic.service.playback;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class PlaybackShutdown {

    static final long FADE_MILLIS = 120;
    static final long RELEASE_BUDGET_MILLIS = 500;
    private static final long NANOS_PER_MILLI = 1_000_000;

    private final Supplier<List<Playback>> players;
    private final LongConsumer pause;
    private final LongSupplier nanos;

    public PlaybackShutdown(Supplier<List<Playback>> players) {
        this(players, PlaybackShutdown::sleep, System::nanoTime);
    }

    PlaybackShutdown(Supplier<List<Playback>> players, LongConsumer pause, LongSupplier nanos) {
        this.players = players;
        this.pause = pause;
        this.nanos = nanos;
    }

    public void run() {
        List<Playback> live = players.get();
        if (fadeOut(live)) pause.accept(FADE_MILLIS);
        live.forEach(PlaybackShutdown::dispose);
        long deadline = nanos.getAsLong() + RELEASE_BUDGET_MILLIS * NANOS_PER_MILLI;
        for (Playback player : live) {
            long remaining = (deadline - nanos.getAsLong()) / NANOS_PER_MILLI;
            if (remaining <= 0) return;
            player.awaitRelease(remaining);
        }
    }

    private static boolean fadeOut(List<Playback> live) {
        boolean sounding = false;
        for (Playback player : live) {
            if (player.getStatus() != PlaybackStatus.PLAYING) continue;
            sounding = true;
            try {
                player.setVolume(0);
            } catch (RuntimeException failure) {
                System.err.println("[shutdown] fade failed: " + failure);
            }
        }
        return sounding;
    }

    private static void dispose(Playback player) {
        try {
            player.dispose();
        } catch (RuntimeException failure) {
            System.err.println("[shutdown] dispose failed: " + failure);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
