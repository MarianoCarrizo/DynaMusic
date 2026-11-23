package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioPrepFailureMemoryTest {

    @TempDir Path dir;

    private final AtomicInteger attempts = new AtomicInteger();
    private boolean converterFails = true;

    private AudioPrep prep() {
        PcmConverter converter = (source, target) -> {
            attempts.incrementAndGet();
            if (converterFails) throw new IOException("corrupt");
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

    private File ogg() throws IOException {
        return Files.write(dir.resolve("bad.ogg"), new byte[16]).toFile();
    }

    @Test
    void aPrefetchNeverRetriesAFileThatAlreadyFailed() throws IOException {
        AudioPrep prep = prep();
        File bad = ogg();
        assertThrows(CompletionException.class, () -> prep.prepare(bad).join());
        int afterFirstFailure = attempts.get();

        assertThrows(CompletionException.class, () -> prep.prefetch(bad).join());
        assertThrows(CompletionException.class, () -> prep.prefetch(bad).join());

        assertEquals(afterFirstFailure, attempts.get());
    }

    @Test
    void anExplicitPlayRetriesAndForgetsTheFailureOnSuccess() throws Exception {
        AudioPrep prep = prep();
        File bad = ogg();
        assertThrows(CompletionException.class, () -> prep.prepare(bad).join());
        int afterFirstFailure = attempts.get();
        converterFails = false;

        PreparedAudio retried = prep.prepare(bad).get();

        assertTrue(attempts.get() > afterFirstFailure);
        assertEquals(0.5, retried.gain());
        assertEquals(retried, prep.prefetch(bad).get());
    }

    @Test
    void prefetchOfAFreshFileStillWorks() throws Exception {
        AudioPrep prep = prep();
        converterFails = false;

        assertEquals(0.5, prep.prefetch(ogg()).get().gain());
    }
}
