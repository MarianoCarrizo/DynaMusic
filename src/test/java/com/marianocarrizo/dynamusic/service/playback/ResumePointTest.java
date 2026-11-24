package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.util.Duration;
import org.junit.jupiter.api.Test;

class ResumePointTest {

    private final FakePlayback old = new FakePlayback(Runnable::run);
    private final FakePlayback reopened = new FakePlayback(Runnable::run);

    @Test
    void capturesThePositionAndTheSoundingState() {
        old.seek(Duration.seconds(42));

        ResumePoint point = ResumePoint.of(old, true);

        assertEquals(Duration.seconds(42), point.position());
        assertEquals(true, point.playing());
    }

    @Test
    void aPlayingTrackResumesAtThePositionAndKeepsPlaying() {
        old.seek(Duration.seconds(42));

        ResumePoint.of(old, true).applyTo(reopened);

        assertEquals(Duration.seconds(42), reopened.getCurrentTime());
        assertEquals(PlaybackStatus.PLAYING, reopened.getStatus());
    }

    @Test
    void aPausedTrackResumesAtThePositionWithoutPlaying() {
        old.seek(Duration.seconds(10));

        ResumePoint.of(old, false).applyTo(reopened);

        assertEquals(Duration.seconds(10), reopened.getCurrentTime());
        assertEquals(PlaybackStatus.UNKNOWN, reopened.getStatus());
    }

    @Test
    void theStartPointPlaysFromTheBeginningWithoutSeeking() {
        ResumePoint.START.applyTo(reopened);

        assertEquals(Duration.ZERO, reopened.getCurrentTime());
        assertEquals(PlaybackStatus.PLAYING, reopened.getStatus());
    }

    @Test
    void anUnreadablePositionFallsBackToTheStart() {
        old.seek(Duration.UNKNOWN);

        assertEquals(Duration.ZERO, ResumePoint.of(old, true).position());
    }
}
