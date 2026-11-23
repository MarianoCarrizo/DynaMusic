package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FfmpegLocatorTest {

    private static final Path ROOT = Path.of("app-root").toAbsolutePath();

    @Test
    void missingExecutableIsNotAvailable() {
        assertFalse(FfmpegLocator.answersVersionQuery(Optional.empty(), 1000));
        assertFalse(FfmpegLocator.answersVersionQuery(Optional.of(ROOT.resolve("no-such-ffmpeg").toString()), 1000));
    }

    @Test
    void aHungProcessIsGivenUpOnAtTheDeadline() {
        Optional<String> ffmpeg = FfmpegLocator.executable();
        assumeTrue(ffmpeg.isPresent(), "ffmpeg not installed");
        List<String> forever = List.of(ffmpeg.get(), "-hide_banner", "-nostdin", "-v", "error", "-re", "-f", "lavfi",
                "-i", "anullsrc", "-t", "60", "-f", "null", "-");
        long started = System.nanoTime();

        boolean answered = FfmpegLocator.answers(forever, 500);

        assertFalse(answered);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000);
    }

    @Test
    void availabilityNeverBlocksAndResolvesToAStableAnswer() {
        long started = System.nanoTime();

        boolean immediate = FfmpegLocator.isAvailable();

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000);
        boolean resolved = FfmpegLocator.awaitAvailability();
        assertEquals(resolved, FfmpegLocator.awaitAvailability());
        assertEquals(resolved, FfmpegLocator.isAvailable());
        assertTrue(immediate || !resolved);
    }

    @Test
    void listenersAreNotifiedOnceResolved() throws InterruptedException {
        CountDownLatch notified = new CountDownLatch(1);

        FfmpegLocator.onResolved(notified::countDown);

        assertTrue(notified.await(10, TimeUnit.SECONDS));
    }

    @Test
    void installRootFromLauncherPath() {
        Path exe = ROOT.resolve("DynaMusic.exe");
        assertEquals(ROOT, FfmpegLocator.installRoot(exe.toString(), "ignored"));
    }

    @Test
    void installRootFromBundledRuntimeWhenNoLauncherProperty() {
        assertEquals(ROOT, FfmpegLocator.installRoot(null, ROOT.resolve("runtime").toString()));
        assertEquals(ROOT, FfmpegLocator.installRoot(" ", ROOT.resolve("runtime").toString()));
    }

    @Test
    void installRootIsNullForPlainJdk() {
        assertNull(FfmpegLocator.installRoot(null, Path.of("jdk-21").toAbsolutePath().toString()));
        assertNull(FfmpegLocator.installRoot(null, null));
    }

    @Test
    void findsBundledFfmpegOnlyWhenTheBinaryExists() {
        Path dir = ROOT.resolve("ffmpeg");
        Set<Path> files = Set.of(dir.resolve("ffmpeg.exe"));
        assertEquals(Optional.of(dir), FfmpegLocator.findBundled(ROOT, "Windows 11", files::contains));
        assertTrue(FfmpegLocator.findBundled(ROOT, "Windows 11", p -> false).isEmpty());
        assertTrue(FfmpegLocator.findBundled(null, "Windows 11", files::contains).isEmpty());
    }

    @Test
    void binaryNameDependsOnOs() {
        assertEquals("ffmpeg.exe", FfmpegLocator.binaryName("Windows 11"));
        assertEquals("ffmpeg", FfmpegLocator.binaryName("Linux"));
        Path dir = ROOT.resolve("ffmpeg");
        assertTrue(FfmpegLocator.findBundled(ROOT, "Linux", p -> p.equals(dir.resolve("ffmpeg"))).isPresent());
        assertTrue(FfmpegLocator.findBundled(ROOT, "Linux", p -> p.equals(dir.resolve("ffmpeg.exe"))).isEmpty());
    }
}
