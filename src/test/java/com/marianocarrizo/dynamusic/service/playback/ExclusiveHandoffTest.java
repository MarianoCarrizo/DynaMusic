package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.ExclusiveFallbackSink;
import com.marianocarrizo.dynamusic.audio.stream.FakeExclusiveSink;
import com.marianocarrizo.dynamusic.audio.stream.FakePcmSource;
import com.marianocarrizo.dynamusic.audio.stream.FakeSharedLines;
import com.marianocarrizo.dynamusic.audio.stream.JavaSoundOpening;
import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExclusiveHandoffTest {

    private static final AudioFormat FORMAT = PcmFormats.signedLittleEndian(16, 44_100, 2);
    private static final int FRAME_BYTES = 4;
    private static final int TOTAL_FRAMES = 44_100 * 60;
    private static final long TIMEOUT_MILLIS = 5000;
    private static final long CLAIM_MILLIS = 50;
    private static final long PACE_MILLIS = 2;

    private final OutputArbiter arbiter = new OutputArbiter();
    private final FakeSharedLines lines = new FakeSharedLines();
    private final FakeExclusiveSink exclusive = new FakeExclusiveSink(FORMAT);
    private final AtomicInteger errors = new AtomicInteger();
    private StreamingPlayback playback;

    @AfterEach
    void tearDown() {
        if (playback != null) playback.dispose();
    }

    private void openPlayback() {
        exclusive.paceWritesBy(PACE_MILLIS);
        ExclusiveFallbackSink sink = new ExclusiveFallbackSink(exclusive, shared -> JavaSoundOpening.open(lines, shared), arbiter,
                CLAIM_MILLIS);
        playback = new StreamingPlayback(new FakePcmSource(FORMAT, new byte[TOTAL_FRAMES * FRAME_BYTES]), sink,
                Runnable::run);
        playback.setOnError(errors::incrementAndGet);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("condition not reached in time");
            Thread.sleep(5);
        }
    }

    @Test
    void aMoodStartingMidTrackMovesPlaybackToSharedWithoutAnError() throws Exception {
        openPlayback();
        playback.play();
        await(() -> exclusive.writes() > 0);

        boolean released = arbiter.sharedStarting(new Object(), OutputArbiter.HANDOFF_MILLIS * 20);
        await(() -> !lines.opened().isEmpty() && lines.last().writeAttempts() > 0);

        assertTrue(released);
        assertEquals(PlaybackStatus.PLAYING, playback.getStatus());
        assertEquals(0, errors.get());
        assertEquals(1, exclusive.suspends());
        await(() -> playback.outputProperty().get().label().equals("16/44.1 shared"));
    }

    @Test
    void listeningStaysSharedForTheRestOfTheTrackAfterTheMoodStops() throws Exception {
        Object mood = new Object();
        openPlayback();
        playback.play();
        await(() -> exclusive.writes() > 0);
        arbiter.sharedStarting(mood, OutputArbiter.HANDOFF_MILLIS * 20);
        await(() -> !lines.opened().isEmpty() && lines.last().writeAttempts() > 0);

        arbiter.sharedStopped(mood);
        int before = lines.last().writeAttempts();
        await(() -> lines.last().writeAttempts() > before + 3);

        assertEquals(1, lines.opened().size());
        assertFalse(arbiter.holdsExclusive());
        assertEquals("16/44.1 shared", playback.outputProperty().get().label());
    }

    @Test
    void theBadgeReadsExclusiveWhilePlayingExclusive() throws Exception {
        openPlayback();

        playback.play();
        await(() -> exclusive.writes() > 0);

        assertEquals("16/44.1 exclusive", playback.outputProperty().get().label());
    }

    @Test
    void anAttenuatedExclusiveStreamIsNotBitPerfectAndRecoversAtFullVolume() throws Exception {
        openPlayback();
        playback.play();
        await(() -> exclusive.writes() > 0);

        playback.setVolume(0.5);
        assertEquals("16/44.1 exclusive · not bit-perfect", playback.outputProperty().get().label());

        playback.setVolume(1.0);
        assertEquals("16/44.1 exclusive", playback.outputProperty().get().label());
    }
}
