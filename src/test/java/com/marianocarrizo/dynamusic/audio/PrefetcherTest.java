package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.cache.AudioCache;
import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import com.marianocarrizo.dynamusic.audio.pcm.GainMeasurer;
import com.marianocarrizo.dynamusic.audio.pcm.PcmConverter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrefetcherTest {

    @TempDir Path dir;

    private final List<String> converted = new ArrayList<>();

    private AudioPrep prep() {
        PcmConverter converter = (source, target) -> {
            if (!source.exists()) throw new IOException("missing source");
            converted.add(source.getName());
            Files.write(target, new byte[8]);
        };
        GainMeasurer gains = new GainMeasurer() {
            @Override
            public double ofWav(File wav) {
                return 0.5;
            }

            @Override
            public OptionalDouble ofNative(File source) {
                return OptionalDouble.of(0.5);
            }
        };
        AudioCache cache = new AudioCache(dir.resolve("cache"), CacheLimit.GB_1, System::currentTimeMillis,
                AudioCache.filesystemDeleter(), flush -> { });
        cache.initialize();
        return new AudioPrep(cache, new RouteResolver(() -> true), converter, converter, gains, () -> true,
                Runnable::run);
    }

    private File flac(String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[16]).toFile();
    }

    @Test
    void nothingStartsUntilTheCurrentTrackIsPrepared() throws IOException {
        Prefetcher prefetcher = new Prefetcher(prep(), Runnable::run);
        CompletableFuture<Object> currentTrack = new CompletableFuture<>();

        prefetcher.schedule(flac("next.flac"), currentTrack);

        assertTrue(converted.isEmpty());
        currentTrack.complete(null);
        assertEquals(List.of("next.flac"), converted);
    }

    @Test
    void onlyTheLatestRequestSurvivesWhileWaiting() throws IOException {
        Prefetcher prefetcher = new Prefetcher(prep(), Runnable::run);
        CompletableFuture<Object> gate = new CompletableFuture<>();

        prefetcher.schedule(flac("old.flac"), gate);
        prefetcher.schedule(flac("latest.flac"), gate);
        gate.complete(null);

        assertEquals(List.of("latest.flac"), converted);
    }

    @Test
    void anAlreadyCachedTrackIsNotConvertedAgain() throws IOException {
        AudioPrep prep = prep();
        Prefetcher prefetcher = new Prefetcher(prep, Runnable::run);
        File next = flac("next.flac");

        prefetcher.schedule(next, CompletableFuture.completedFuture(null));
        prefetcher.schedule(next, CompletableFuture.completedFuture(null));

        assertEquals(1, converted.size());
    }

    @Test
    void unplayableTracksAreSkippedSilently() throws IOException {
        Prefetcher prefetcher = new Prefetcher(prep(), Runnable::run);

        prefetcher.schedule(new File("notes.txt"), CompletableFuture.completedFuture(null));
        prefetcher.schedule(flac("after.flac"), CompletableFuture.completedFuture(null));

        assertEquals(List.of("after.flac"), converted);
    }

    @Test
    void cancelDropsTheWaitingRequest() throws IOException {
        Prefetcher prefetcher = new Prefetcher(prep(), Runnable::run);
        CompletableFuture<Object> gate = new CompletableFuture<>();

        prefetcher.schedule(flac("next.flac"), gate);
        prefetcher.cancel();
        gate.complete(null);

        assertTrue(converted.isEmpty());
    }

    @Test
    void aFailingPrefetchDoesNotStopLaterOnes() throws IOException {
        Prefetcher prefetcher = new Prefetcher(prep(), Runnable::run);
        File missing = new File(dir.toFile(), "missing.flac");

        prefetcher.schedule(missing, CompletableFuture.completedFuture(null));
        prefetcher.schedule(flac("good.flac"), CompletableFuture.completedFuture(null));

        assertEquals(List.of("good.flac"), converted);
    }
}
