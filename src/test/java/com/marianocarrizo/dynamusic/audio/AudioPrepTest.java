package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.cache.AudioCache;
import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import com.marianocarrizo.dynamusic.audio.pcm.GainMeasurer;
import com.marianocarrizo.dynamusic.audio.pcm.PcmConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioPrepTest {

    private static final double MEASURED_GAIN = 0.35;

    @TempDir Path dir;

    private final AtomicInteger conversions = new AtomicInteger();
    private final AtomicInteger measurements = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);
    private final Executor background = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });
    private boolean ffmpeg = true;
    private boolean javaSoundFails;
    private boolean blockConversion;
    private boolean nativeMeasurable = true;

    private final PcmConverter javaSound = (source, target) -> {
        conversions.incrementAndGet();
        if (javaSoundFails) throw new IOException("jflac failed");
        write(target);
    };
    private final PcmConverter ffmpegConverter = (source, target) -> {
        conversions.incrementAndGet();
        if (blockConversion) await();
        write(target);
    };
    private final GainMeasurer gains = new GainMeasurer() {
        @Override
        public double ofWav(File wav) {
            measurements.incrementAndGet();
            return MEASURED_GAIN;
        }

        @Override
        public OptionalDouble ofNative(File source) {
            measurements.incrementAndGet();
            return nativeMeasurable ? OptionalDouble.of(0.6) : OptionalDouble.empty();
        }
    };

    @AfterEach
    void releaseBlockedWork() {
        release.countDown();
    }

    private void await() throws IOException {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            throw new IOException(interrupted);
        }
    }

    private static void write(Path target) throws IOException {
        Files.write(target, new byte[64]);
    }

    private AudioPrep prep() {
        AudioCache cache = new AudioCache(dir.resolve("cache"), CacheLimit.GB_1, System::currentTimeMillis,
                AudioCache.filesystemDeleter(), flush -> { });
        cache.initialize();
        return new AudioPrep(cache, new RouteResolver(() -> ffmpeg), javaSound, ffmpegConverter, gains,
                () -> ffmpeg, Runnable::run);
    }

    private File file(String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[16]).toFile();
    }

    @Test
    void secondRequestIsAHitAndDoesNotConvertOrMeasureAgain() throws Exception {
        AudioPrep prep = prep();
        File flac = file("a.flac");

        PreparedAudio first = prep.prepare(flac).get();
        int measuredAfterFirst = measurements.get();
        PreparedAudio second = prep.prepare(flac).get();

        assertEquals(1, conversions.get());
        assertEquals(measuredAfterFirst, measurements.get());
        assertEquals(first, second);
        assertEquals(MEASURED_GAIN, second.gain());
        assertTrue(second.playable().getName().endsWith(".wav"));
    }

    @Test
    void nativeFormatsPlayFromTheOriginalFileWithAMeasuredCachedGain() throws Exception {
        AudioPrep prep = prep();
        File mp3 = file("a.mp3");

        PreparedAudio first = prep.prepare(mp3).get();
        PreparedAudio second = prep.prepare(mp3).get();

        assertEquals(mp3, first.playable());
        assertEquals(0.6, first.gain());
        assertEquals(0.6, second.gain());
        assertEquals(1, measurements.get());
        assertEquals(0, conversions.get());
    }

    @Test
    void unmeasurableNativeFilesUseTheFallbackGainWithoutCachingIt() throws Exception {
        nativeMeasurable = false;
        AudioPrep prep = prep();
        File mp3 = file("a.mp3");

        assertEquals(AudioPrep.FALLBACK_GAIN, prep.prepare(mp3).get().gain());
        nativeMeasurable = true;
        assertEquals(0.6, prep.prepare(mp3).get().gain());
    }

    @Test
    void concurrentRequestsForTheSameFileShareOneConversion() throws Exception {
        blockConversion = true;
        ffmpeg = true;
        AudioCache cache = new AudioCache(dir.resolve("cache"), CacheLimit.GB_1, System::currentTimeMillis,
                AudioCache.filesystemDeleter(), flush -> { });
        cache.initialize();
        AudioPrep prep = new AudioPrep(cache, new RouteResolver(() -> true), javaSound, ffmpegConverter, gains,
                () -> true, background);
        File ogg = file("a.ogg");

        CompletableFuture<PreparedAudio> prefetch = prep.prepare(ogg, background);
        CompletableFuture<PreparedAudio> play = prep.prepare(ogg);
        release.countDown();

        assertSame(prefetch, play);
        assertEquals(prefetch.get(10, TimeUnit.SECONDS), play.get(10, TimeUnit.SECONDS));
        assertEquals(1, conversions.get());
    }

    @Test
    void sharedFailureIsSeenByEveryCallerAndAllowsARetry() throws Exception {
        javaSoundFails = false;
        ffmpeg = false;
        AudioPrep prep = prep();
        File ogg = file("a.ogg");

        CompletionException first = assertThrows(CompletionException.class, () -> prep.prepare(ogg).join());
        CompletionException second = assertThrows(CompletionException.class, () -> prep.prepare(ogg).join());

        assertTrue(first.getCause() instanceof UnplayableAudioException);
        assertTrue(second.getCause().getMessage().contains("ffmpeg"));
        ffmpeg = true;
        assertEquals(MEASURED_GAIN, prep.prepare(ogg).get().gain());
    }

    @Test
    void ffmpegFormatsAreUnplayableWithoutFfmpegButFlacAndMp3StillPlay() throws Exception {
        ffmpeg = false;
        AudioPrep prep = prep();

        CompletionException failure = assertThrows(CompletionException.class, () -> prep.prepare(file("a.opus")).join());

        assertTrue(failure.getCause().getMessage().startsWith("OPUS"));
        assertEquals(MEASURED_GAIN, prep.prepare(file("a.flac")).get().gain());
        assertEquals(0.6, prep.prepare(file("a.mp3")).get().gain());
    }

    @Test
    void flacFallsBackToFfmpegWhenTheJavaDecoderFails() throws Exception {
        javaSoundFails = true;
        AudioPrep prep = prep();

        PreparedAudio prepared = prep.prepare(file("a.flac")).get();

        assertEquals(2, conversions.get());
        assertEquals(MEASURED_GAIN, prepared.gain());
    }

    @Test
    void flacFailureWithoutFfmpegIsUnplayableAndLeavesNoEntry() throws Exception {
        javaSoundFails = true;
        ffmpeg = false;
        AudioPrep prep = prep();
        File flac = file("a.flac");

        assertThrows(CompletionException.class, () -> prep.prepare(flac).join());

        try (var files = Files.list(dir.resolve("cache"))) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".wav") || path.toString().endsWith(".tmp")));
        }
    }

    @Test
    void unsupportedFilesFailWithAMessage() throws Exception {
        AudioPrep prep = prep();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> prep.prepare(file("notes.txt")).join());

        assertTrue(failure.getCause().getMessage().contains("notes.txt"));
    }
}
