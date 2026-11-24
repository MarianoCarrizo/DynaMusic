package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

class PlaybackSeamTest {

    private final Queue<Runnable> queued = new ArrayDeque<>();
    private final Executor deferred = queued::add;
    private final List<String> events = new ArrayList<>();

    private void drain() {
        while (!queued.isEmpty()) queued.remove().run();
    }

    @Test
    void readyCallbackRegisteredOnAReadyPlaybackFiresOnceThroughTheExecutor() {
        FakePlayback playback = new FakePlayback(deferred);
        playback.becomeReady();

        playback.setOnReady(() -> events.add("ready"));

        assertTrue(events.isEmpty());
        drain();
        drain();
        assertEquals(List.of("ready"), events);
    }

    @Test
    void readyCallbackRegisteredBeforeReadyFiresOnceWhenItBecomesReady() {
        FakePlayback playback = new FakePlayback(deferred);
        playback.setOnReady(() -> events.add("ready"));
        drain();
        assertTrue(events.isEmpty());

        playback.becomeReady();
        drain();

        assertEquals(List.of("ready"), events);
    }

    @Test
    void lateRegistrationAfterReadyStillFiresExactlyOnce() {
        FakePlayback playback = new FakePlayback(deferred);
        playback.becomeReady();
        drain();

        playback.setOnReady(() -> events.add("late"));
        drain();

        assertEquals(List.of("late"), events);
    }

    @Test
    void spectrumAttachAndDetachNeverThrow() {
        FakePlayback playback = new FakePlayback(deferred);

        assertDoesNotThrow(() -> playback.attachSpectrum(new AudioAnalyzer()));
        assertDoesNotThrow(playback::detachSpectrum);
        assertEquals(0, playback.spectrumAttachments());
    }

    @Test
    void volumeSetThroughTheVolumePropertyIsReadBackAndNotified() {
        FakePlayback playback = new FakePlayback(deferred);
        List<Double> seen = new ArrayList<>();
        playback.volumeProperty().addListener((obs, oldValue, newValue) -> seen.add(newValue.doubleValue()));

        playback.volumeProperty().set(0.4);
        playback.setVolume(0.2);

        assertEquals(0.2, playback.getVolume(), 1e-9);
        assertEquals(List.of(0.4, 0.2), seen);
    }

    @Test
    void seekMovesTheCurrentTime() {
        FakePlayback playback = new FakePlayback(deferred);

        playback.seek(Duration.seconds(3));

        assertEquals(3000.0, playback.getCurrentTime().toMillis(), 1e-9);
    }
}
