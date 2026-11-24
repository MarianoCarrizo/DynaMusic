package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.PreparedAudio;
import com.marianocarrizo.dynamusic.audio.stream.FakePcmSink;
import com.marianocarrizo.dynamusic.audio.stream.FakePcmSource;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaybackRouterTest {

    private static final AudioFormat FORMAT = PcmFormats.signedLittleEndian(16, 44100, 2);
    private static final double PREPARED_GAIN = 0.5;

    @TempDir Path dir;

    private final OutputArbiter arbiter = new OutputArbiter();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger prepares = new AtomicInteger();
    private final AtomicInteger opens = new AtomicInteger();
    private final FakePcmSource source = new FakePcmSource(FORMAT, new byte[400]);
    private final FakePcmSink sink = new FakePcmSink(FORMAT);
    private StreamOpener opener = file -> {
        opens.incrementAndGet();
        return Optional.of(new PcmStream(source, sink));
    };

    private PlaybackRouter router() {
        return new PlaybackRouter(enabled::get, file -> {
            prepares.incrementAndGet();
            return CompletableFuture.completedFuture(new PreparedAudio(file, PREPARED_GAIN));
        }, file -> opener.open(file), Runnable::run, Runnable::run, arbiter);
    }

    private static File flacFixture() {
        return new File("src/test/resources/audio/flac-16bit-seektable.flac");
    }

    @Test
    void settingOffUsesTheJavaFxPlanWithPreparedAudio() {
        enabled.set(false);

        PlaybackPlan plan = router().plan(flacFixture(), PlaybackUse.LISTEN).join();

        JavaFxPlan javaFx = assertInstanceOf(JavaFxPlan.class, plan);
        assertEquals(PREPARED_GAIN, javaFx.gain());
        assertEquals(Optional.of(flacFixture()), javaFx.held());
        assertEquals(0, opens.get());
    }

    @Test
    void settingOnStreamsListenPathsWithoutPreparingAudio() {
        PlaybackPlan plan = router().plan(flacFixture(), PlaybackUse.LISTEN).join();

        StreamingPlan streaming = assertInstanceOf(StreamingPlan.class, plan);
        assertSame(source, streaming.stream().source());
        assertEquals(1.0, streaming.gain());
        assertEquals(Optional.empty(), streaming.held());
        assertEquals(0, prepares.get());
    }

    @Test
    void moodsStayOnJavaFxEvenWhenEnabled() {
        PlaybackPlan plan = router().plan(flacFixture(), PlaybackUse.MOOD).join();

        assertInstanceOf(JavaFxPlan.class, plan);
        assertEquals(0, opens.get());
    }

    @Test
    void otherFormatsStayOnJavaFx() {
        PlaybackPlan plan = router().plan(new File("song.mp3"), PlaybackUse.LISTEN).join();

        assertInstanceOf(JavaFxPlan.class, plan);
        assertEquals(0, opens.get());
    }

    @Test
    void aTruncatedHeaderFallsBackToJavaFxBeforeOpeningAnything() throws IOException {
        Path truncated = dir.resolve("truncated.flac");
        Files.write(truncated, new byte[] {0x66, 0x4C});

        PlaybackPlan plan = router().plan(truncated.toFile(), PlaybackUse.LISTEN).join();

        assertInstanceOf(JavaFxPlan.class, plan);
        assertEquals(0, opens.get());
        assertEquals(1, prepares.get());
    }

    @Test
    void anOpenerThatCannotOpenFallsBackToJavaFx() {
        opener = file -> Optional.empty();

        PlaybackPlan plan = router().plan(flacFixture(), PlaybackUse.LISTEN).join();

        assertInstanceOf(JavaFxPlan.class, plan);
        assertEquals(1, prepares.get());
    }

    @Test
    void anOpenerThatThrowsFallsBackToJavaFx() {
        opener = file -> {
            throw new IllegalStateException("device exploded");
        };

        PlaybackPlan plan = router().plan(flacFixture(), PlaybackUse.LISTEN).join();

        assertInstanceOf(JavaFxPlan.class, plan);
    }

    @Test
    void togglingTheSettingAffectsOnlyPlansRequestedAfterwards() {
        PlaybackRouter router = router();
        enabled.set(false);
        PlaybackPlan before = router.plan(flacFixture(), PlaybackUse.LISTEN).join();
        enabled.set(true);
        PlaybackPlan after = router.plan(flacFixture(), PlaybackUse.LISTEN).join();

        assertInstanceOf(JavaFxPlan.class, before);
        assertInstanceOf(StreamingPlan.class, after);
    }

    @Test
    void streamsAnswersFromTheSettingAndTheProbe() {
        PlaybackRouter router = router();

        assertTrue(router.streams(flacFixture()));
        enabled.set(false);
        assertEquals(false, router.streams(flacFixture()));
    }

    @Test
    void streamsIsFalseForAStreamableExtensionTheProbeRejects() throws IOException {
        Path broken = dir.resolve("broken.flac");
        Files.write(broken, new byte[64]);

        assertEquals(false, router().streams(broken.toFile()));
        assertEquals(false, router().streamsAsync(broken.toFile()).join());
    }

    @Test
    void streamsAsyncAgreesWithTheProbeForAStreamableFile() {
        assertTrue(router().streamsAsync(flacFixture()).join());
        enabled.set(false);
        assertEquals(false, router().streamsAsync(flacFixture()).join());
    }

    @Test
    void javaFxPlansCarryTheRoutersArbiter() {
        enabled.set(false);

        JavaFxPlan javaFx = assertInstanceOf(JavaFxPlan.class,
                router().plan(flacFixture(), PlaybackUse.MOOD).join());

        assertSame(arbiter, javaFx.arbiter());
    }
}
