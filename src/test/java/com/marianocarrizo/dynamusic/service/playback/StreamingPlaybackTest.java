package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.FakePcmSink;
import com.marianocarrizo.dynamusic.audio.stream.FakePcmSource;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import com.marianocarrizo.dynamusic.service.AudioAnalyzer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javafx.util.Duration;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StreamingPlaybackTest {

    private static final AudioFormat FORMAT = PcmFormats.signedLittleEndian(16, 44100, 2);
    private static final int FRAME_BYTES = 4;
    private static final int RAMP_FRAMES = 441;
    private static final int TOTAL_FRAMES = 44100;
    private static final int CONSTANT_SAMPLE = 1000;
    private static final long TIMEOUT_MILLIS = 5000;
    private static final long SETTLE_MILLIS = 150;

    private FakePcmSink sink;
    private FakePcmSource source;
    private StreamingPlayback playback;
    private final AtomicInteger ends = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private final AtomicInteger readies = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (playback != null) playback.dispose();
    }

    private static byte[] constantPcm(int frames, int sample) {
        byte[] data = new byte[frames * FRAME_BYTES];
        for (int i = 0; i < data.length; i += 2) {
            data[i] = (byte) sample;
            data[i + 1] = (byte) (sample >> 8);
        }
        return data;
    }

    private static byte[] patternPcm(int frames) {
        byte[] data = new byte[frames * FRAME_BYTES];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31 + i / 7);
        }
        return data;
    }

    private void open(byte[] pcm) {
        source = new FakePcmSource(FORMAT, pcm);
        sink = new FakePcmSink(FORMAT);
        playback = new StreamingPlayback(source, sink, Runnable::run);
        playback.setOnEndOfMedia(ends::incrementAndGet);
        playback.setOnError(errors::incrementAndGet);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("condition not reached in time");
            Thread.sleep(5);
        }
    }

    private static void settle() throws InterruptedException {
        Thread.sleep(SETTLE_MILLIS);
    }

    private static int sampleAt(byte[] data, int index) {
        return (short) ((data[index * 2] & 0xFF) | (data[index * 2 + 1] << 8));
    }

    @Test
    void readyCallbackRegisteredAfterConstructionFiresOnce() throws Exception {
        open(constantPcm(100, 0));

        playback.setOnReady(readies::incrementAndGet);
        settle();

        assertEquals(1, readies.get());
        assertEquals(PlaybackStatus.READY, playback.getStatus());
    }

    @Test
    void readyCallbackRegisteredBeforeDeliveryFiresOnce() throws Exception {
        source = new FakePcmSource(FORMAT, constantPcm(100, 0));
        sink = new FakePcmSink(FORMAT);
        ArrayDeque<Runnable> deferred = new ArrayDeque<>();
        playback = new StreamingPlayback(source, sink, deferred::add);

        playback.setOnReady(readies::incrementAndGet);
        deferred.forEach(Runnable::run);

        assertEquals(1, readies.get());
    }

    @Test
    void totalDurationComesFromTheSource() {
        open(constantPcm(TOTAL_FRAMES, 0));

        assertEquals(1000.0, playback.getTotalDuration().toMillis(), 0.001);
    }

    @Test
    void firstBlockIsWrittenBeforeTheSourceIsFullyRead() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        CountDownLatch gate = new CountDownLatch(1);
        sink.blockWrites(gate);

        playback.play();
        await(() -> sink.writeAttempts() >= 1);
        long framesReadSoFar = source.positionFrame();
        gate.countDown();

        assertTrue(framesReadSoFar < TOTAL_FRAMES, "read " + framesReadSoFar);
    }

    @Test
    void volumeScalesSamplesWithoutNormalization() throws Exception {
        open(constantPcm(TOTAL_FRAMES, CONSTANT_SAMPLE));
        playback.setVolume(0.5);

        playback.play();
        await(() -> ends.get() == 1);

        byte[] written = sink.bytes();
        assertEquals(CONSTANT_SAMPLE / 2, sampleAt(written, written.length / 2 - 2));
    }

    @Test
    void volumeChangeReachesTheSinkOnTheNextPlaythrough() throws Exception {
        open(constantPcm(TOTAL_FRAMES, CONSTANT_SAMPLE));
        playback.play();
        await(() -> ends.get() == 1);

        playback.setVolume(0.25);
        playback.seek(Duration.ZERO);
        await(() -> source.seeks().size() == 1);
        await(() -> ends.get() == 2);

        byte[] written = sink.bytes();
        assertEquals(CONSTANT_SAMPLE / 4, sampleAt(written, written.length / 2 - 2));
    }

    @Test
    void endOfMediaDrainsOnceAndKeepsPlayingStatus() throws Exception {
        byte[] pcm = patternPcm(TOTAL_FRAMES / 2);
        open(pcm);

        playback.play();
        await(() -> ends.get() == 1);
        settle();

        int rampBytes = (RAMP_FRAMES + 1) * FRAME_BYTES;
        byte[] written = sink.bytes();
        assertEquals(pcm.length, written.length);
        assertArrayEquals(Arrays.copyOfRange(pcm, rampBytes, pcm.length), Arrays.copyOfRange(written, rampBytes, pcm.length));
        assertEquals(1, ends.get());
        assertEquals(1, sink.drains());
        assertEquals(PlaybackStatus.PLAYING, playback.getStatus());
    }

    @Test
    void seekFlushesTheSinkRebasesTimeAndResumesAtTheTarget() throws Exception {
        byte[] pcm = patternPcm(TOTAL_FRAMES);
        open(pcm);

        playback.seek(Duration.seconds(0.5));

        assertEquals(500.0, playback.getCurrentTime().toMillis(), 0.001);
        await(() -> source.seeks().size() == 1);
        assertEquals(22050L, source.seeks().get(0));
        assertTrue(sink.flushes() >= 2);
        playback.play();
        await(() -> ends.get() == 1);
        int tailFrames = TOTAL_FRAMES - 22050;
        int rampBytes = (RAMP_FRAMES + 1) * FRAME_BYTES;
        byte[] expectedTail = Arrays.copyOfRange(pcm, 22050 * FRAME_BYTES, pcm.length);
        byte[] written = sink.bytes();
        assertEquals(tailFrames * FRAME_BYTES, written.length);
        assertArrayEquals(Arrays.copyOfRange(expectedTail, rampBytes, expectedTail.length),
                Arrays.copyOfRange(written, rampBytes, written.length));
    }

    @Test
    void seekBeyondTheEndIsClampedToTheDuration() throws Exception {
        open(constantPcm(TOTAL_FRAMES, 0));

        playback.seek(Duration.seconds(99));

        assertEquals(1000.0, playback.getCurrentTime().toMillis(), 0.001);
        await(() -> source.seeks().size() == 1);
        assertEquals((long) TOTAL_FRAMES, source.seeks().get(0));
    }

    @Test
    void pauseStopsConsumptionAndResumeContinues() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        CountDownLatch gate = new CountDownLatch(1);
        sink.blockWrites(gate);

        playback.play();
        await(() -> sink.writeAttempts() >= 1);
        int stopsBefore = sink.stops();
        playback.pause();
        gate.countDown();
        await(() -> sink.stops() > stopsBefore);
        long frozen = source.positionFrame();
        settle();

        assertEquals(PlaybackStatus.PAUSED, playback.getStatus());
        assertEquals(frozen, source.positionFrame());
        playback.play();
        await(() -> ends.get() == 1);
        assertEquals((long) TOTAL_FRAMES * 4, source.positionFrame());
    }

    @Test
    void resumeRewindsTheSourceToTheFramePlayedAtPause() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        CountDownLatch gate = new CountDownLatch(1);
        sink.blockWrites(gate);

        playback.play();
        await(() -> sink.writeAttempts() >= 1);
        sink.playFrames(1000);
        int stopsBefore = sink.stops();
        playback.pause();
        gate.countDown();
        await(() -> sink.stops() > stopsBefore);
        assertEquals(0, source.seeks().size());

        playback.play();

        await(() -> source.seeks().size() == 1);
        assertEquals(1000L, source.seeks().get(0));
        assertEquals(1, sink.flushes() - 1);
    }

    @Test
    void currentTimeFollowsTheSinkPosition() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        CountDownLatch gate = new CountDownLatch(1);
        sink.blockWrites(gate);

        playback.play();
        await(() -> sink.writeAttempts() >= 1);
        sink.playFrames(4410);
        gate.countDown();

        await(() -> playback.getCurrentTime().toMillis() >= 99);
        assertEquals(100.0, playback.getCurrentTime().toMillis(), 1.0);
    }

    @Test
    void stopReturnsToTheStartAndStaysSilent() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        CountDownLatch gate = new CountDownLatch(1);
        sink.blockWrites(gate);
        playback.play();
        await(() -> sink.writeAttempts() >= 1);

        playback.stop();
        gate.countDown();

        assertEquals(PlaybackStatus.STOPPED, playback.getStatus());
        assertEquals(0.0, playback.getCurrentTime().toMillis(), 0.001);
        await(() -> source.seeks().size() == 1);
        assertEquals(0L, source.seeks().get(0));
        long frozen = source.positionFrame();
        settle();
        assertEquals(frozen, source.positionFrame());
    }

    @Test
    void sourceFailureEmitsOneErrorAndReleasesResources() throws Exception {
        open(constantPcm(TOTAL_FRAMES, CONSTANT_SAMPLE));
        source.failOnRead(3);

        playback.play();
        await(() -> errors.get() == 1);
        await(() -> sink.closes() == 1);
        settle();

        assertEquals(1, errors.get());
        assertEquals(PlaybackStatus.HALTED, playback.getStatus());
        assertNotNull(playback.getError());
        assertEquals(1, source.closes());
        assertEquals(1, sink.closes());
        assertTrue(playback.awaitTermination(TIMEOUT_MILLIS));
    }

    @Test
    void disposeTwiceDuringPlaybackClosesOnceWithoutError() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        CountDownLatch gate = new CountDownLatch(1);
        sink.blockWrites(gate);
        playback.play();
        await(() -> sink.writeAttempts() >= 1);

        playback.dispose();
        playback.dispose();
        gate.countDown();

        assertTrue(playback.awaitTermination(TIMEOUT_MILLIS));
        assertEquals(1, source.closes());
        assertEquals(1, sink.closes());
        assertEquals(0, errors.get());
        assertEquals(PlaybackStatus.DISPOSED, playback.getStatus());
    }

    @Test
    void commandsAfterDisposeAreIgnored() throws Exception {
        open(constantPcm(100, 0));
        playback.dispose();
        assertTrue(playback.awaitTermination(TIMEOUT_MILLIS));

        assertDoesNotThrow(() -> {
            playback.play();
            playback.pause();
            playback.seek(Duration.seconds(1));
            playback.stop();
        });
        assertEquals(PlaybackStatus.DISPOSED, playback.getStatus());
    }

    @Test
    void workerThreadIsADaemonWithTheStreamName() throws Exception {
        open(constantPcm(100, 0));

        boolean found = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> StreamingPlayback.THREAD_NAME.equals(thread.getName()) && thread.isDaemon());

        assertTrue(found);
    }

    @Test
    void spectrumAttachmentIsAcceptedAndNeverInvoked() {
        open(constantPcm(100, 0));
        AudioAnalyzer analyzer = new AudioAnalyzer();

        assertDoesNotThrow(() -> {
            playback.attachSpectrum(analyzer);
            playback.detachSpectrum();
        });
    }

    @Test
    void volumePropertyMirrorsSetVolume() {
        open(constantPcm(100, 0));

        playback.volumeProperty().set(0.3);

        assertEquals(0.3, playback.getVolume(), 0.0);
    }

    @Test
    void pauseReleasesTheDeviceAndResumeReacquiresIt() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        playback.play();
        await(() -> sink.starts() == 1);

        playback.pause();
        await(() -> sink.suspends() == 1);
        playback.play();

        await(() -> sink.starts() == 2);
        assertEquals(0, errors.get());
    }

    @Test
    void stopReleasesTheDevice() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        playback.play();
        await(() -> sink.starts() == 1);

        playback.stop();

        await(() -> sink.suspends() == 1);
        assertEquals(0, errors.get());
    }

    @Test
    void seekingWhilePausedReleasesTheDevice() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));

        playback.seek(Duration.seconds(1));

        await(() -> sink.suspends() == 1);
        assertEquals(0, sink.starts());
    }

    @Test
    void seekingWhilePlayingKeepsPlayingWithoutAReleaseOfTheDevice() throws Exception {
        open(constantPcm(TOTAL_FRAMES * 4, CONSTANT_SAMPLE));
        playback.play();
        await(() -> sink.starts() == 1);

        playback.seek(Duration.seconds(1));

        await(() -> sink.starts() == 2);
        assertEquals(0, sink.suspends());
    }

    @Test
    void theOutputStartsAsTheSinkReportsIt() {
        open(constantPcm(100, 0));

        assertEquals("16/44.1 shared", playback.outputProperty().get().label());
    }

    @Test
    void aSharedOutputKeepsItsLabelWhenTheVolumeDrops() {
        open(constantPcm(100, 0));

        playback.setVolume(0.5);

        assertEquals("16/44.1 shared", playback.outputProperty().get().label());
        assertFalse(playback.outputProperty().get().bitPerfect());
    }

    @Test
    void theOutputIsAttenuatedBelowFullVolumeAndRecoversAtFullVolume() {
        open(constantPcm(100, 0));

        playback.setVolume(0.5);
        boolean attenuated = playback.outputProperty().get().attenuated();
        playback.setVolume(1.0);

        assertTrue(attenuated);
        assertFalse(playback.outputProperty().get().attenuated());
    }
}
