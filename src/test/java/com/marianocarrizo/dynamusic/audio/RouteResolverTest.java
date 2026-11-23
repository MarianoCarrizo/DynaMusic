package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteResolverTest {

    @TempDir Path dir;

    private File write(String name, byte[] bytes) throws IOException {
        return Files.write(dir.resolve(name), bytes).toFile();
    }

    private static RouteResolver resolver(boolean ffmpeg) {
        return new RouteResolver(() -> ffmpeg);
    }

    @Test
    void m4aWithAacStaysNative() throws IOException {
        File file = write("a.m4a", AudioFixtures.m4a("mp4a", false));

        assertEquals(PlaybackRoute.NATIVE, resolver(true).resolve(file).orElseThrow());
    }

    @Test
    void m4aWithAlacGoesThroughFfmpegWhenSampleDescriptionIsAfterMediaData() throws IOException {
        File file = write("a.m4a", AudioFixtures.m4a("alac", true));

        assertEquals(PlaybackRoute.FFMPEG, resolver(true).resolve(file).orElseThrow());
    }

    @Test
    void sixteenBitWavIsNativeAndTwentyFourBitToo() throws IOException {
        File cd = write("cd.wav", AudioFixtures.wav(16, 44100, 2, false));
        File studio = write("studio.wav", AudioFixtures.wav(24, 96000, 2, false));

        assertEquals(PlaybackRoute.NATIVE, resolver(true).resolve(cd).orElseThrow());
        assertEquals(PlaybackRoute.NATIVE, resolver(true).resolve(studio).orElseThrow());
    }

    @Test
    void thirtyTwoBitIntegerWavUsesJavaSound() throws IOException {
        File file = write("deep.wav", AudioFixtures.wav(32, 44100, 2, false));

        assertEquals(PlaybackRoute.JAVA_SOUND, resolver(true).resolve(file).orElseThrow());
    }

    @Test
    void highRateWavIsResampledByFfmpegWhenAvailable() throws IOException {
        File file = write("hires.wav", AudioFixtures.wav(24, 192000, 2, false));

        assertEquals(PlaybackRoute.FFMPEG, resolver(true).resolve(file).orElseThrow());
    }

    @Test
    void highRateWavStaysNativeWithoutFfmpeg() throws IOException {
        File file = write("hires.wav", AudioFixtures.wav(24, 192000, 2, false));

        assertEquals(PlaybackRoute.NATIVE, resolver(false).resolve(file).orElseThrow());
    }

    @Test
    void highRateAiffFollowsTheSameRule() throws IOException {
        File file = write("hires.aiff", AudioFixtures.aiff(24, 192000));

        assertEquals(PlaybackRoute.FFMPEG, resolver(true).resolve(file).orElseThrow());
        assertEquals(PlaybackRoute.NATIVE, resolver(false).resolve(file).orElseThrow());
    }

    @Test
    void flacUsesJavaSoundUnlessItsRateIsTooHigh() throws IOException {
        File normal = write("normal.flac", AudioFixtures.flacHeader(44100, 16));
        File hires = write("hires.flac", AudioFixtures.flacHeader(192000, 24));

        assertEquals(PlaybackRoute.JAVA_SOUND, resolver(true).resolve(normal).orElseThrow());
        assertEquals(PlaybackRoute.FFMPEG, resolver(true).resolve(hires).orElseThrow());
        assertEquals(PlaybackRoute.JAVA_SOUND, resolver(false).resolve(hires).orElseThrow());
    }

    @Test
    void unreadableFilesFallBackToTheFormatRoute() {
        File missing = dir.resolve("missing.wav").toFile();

        assertEquals(PlaybackRoute.NATIVE, resolver(true).resolve(missing).orElseThrow());
    }

    @Test
    void formatsWithoutProbesUseTheirRegistryRoute() {
        assertEquals(PlaybackRoute.FFMPEG, resolver(true).resolve(new File("a.ogg")).orElseThrow());
        assertEquals(PlaybackRoute.NATIVE, resolver(true).resolve(new File("a.mp3")).orElseThrow());
        assertTrue(resolver(true).resolve(new File("a.txt")).isEmpty());
    }
}
