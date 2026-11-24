package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.marianocarrizo.dynamusic.service.FfmpegLocator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class FfmpegResampleSourceTest {

    private static final AudioFormat TARGET = PcmFormats.signedLittleEndian(16, 48000, 2);
    private static final File INPUT = new File("track.flac");
    private static final long TOTAL_FRAMES = 48000;

    private final List<List<String>> commands = new ArrayList<>();
    private final List<FakeFfmpegProcess> processes = new ArrayList<>();

    private FfmpegResampleSource.Launcher launcher(byte[]... outputs) {
        return command -> {
            commands.add(command);
            FakeFfmpegProcess process = new FakeFfmpegProcess(outputs[Math.min(processes.size(), outputs.length - 1)]);
            processes.add(process);
            return process;
        };
    }

    private static byte[] ramp(int bytes) {
        byte[] data = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    @Test
    void firstLaunchStartsAtTheBeginningWithoutSeekOption() throws Exception {
        new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES, launcher(new byte[0])).close();

        List<String> command = commands.get(0);
        assertFalse(command.contains("-ss"));
        assertEquals("ffmpeg", command.get(0));
        assertTrue(command.containsAll(List.of("-ar", "48000", "-ac", "2", "-f", "s16le", "pipe:1")));
        assertEquals(INPUT.getPath(), command.get(command.indexOf("-i") + 1));
    }

    @Test
    void readsAllBytesFromTheProcess() throws Exception {
        byte[] data = ramp(400);
        try (FfmpegResampleSource source = new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES,
                launcher(data))) {
            assertArrayEquals(data, FlacFixtures.readAll(source));
        }
    }

    @Test
    void partialTrailingFrameIsDropped() throws Exception {
        byte[] data = ramp(403);
        try (FfmpegResampleSource source = new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES,
                launcher(data))) {
            assertEquals(400, FlacFixtures.readAll(source).length);
        }
    }

    @Test
    void seekRespawnsWithStartTimeAndReleasesTheOldProcess() throws Exception {
        byte[] first = ramp(400);
        byte[] second = new byte[80];
        try (FfmpegResampleSource source = new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES,
                launcher(first, second))) {
            source.read(new byte[40]);
            source.seekToFrame(24000);

            List<String> command = commands.get(1);
            assertEquals("0.500000", command.get(command.indexOf("-ss") + 1));
            assertTrue(command.indexOf("-ss") < command.indexOf("-i"));
            assertTrue(processes.get(0).destroyed());
            assertFalse(processes.get(1).destroyed());
            assertEquals(80, FlacFixtures.readAll(source).length);
        }
        assertTrue(processes.get(1).destroyed());
    }

    @Test
    void seekBeyondTheEndIsClampedToTotalFrames() throws Exception {
        try (FfmpegResampleSource source = new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES,
                launcher(new byte[0]))) {
            source.seekToFrame(TOTAL_FRAMES * 4);

            List<String> command = commands.get(1);
            assertEquals("1.000000", command.get(command.indexOf("-ss") + 1));
        }
    }

    @Test
    void closeIsIdempotentAndReadsEnd() throws Exception {
        FfmpegResampleSource source = new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES,
                launcher(ramp(40)));

        source.close();
        source.close();

        assertTrue(processes.get(0).destroyed());
        assertEquals(PcmSource.END_OF_STREAM, source.read(new byte[16]));
    }

    @Test
    void nonZeroExitAtEndOfOutputFailsWithTheStderrTail() throws Exception {
        FfmpegResampleSource.Launcher failing = command -> {
            FakeFfmpegProcess process = new FakeFfmpegProcess(ramp(40), 1, "Invalid data found");
            processes.add(process);
            return process;
        };
        try (FfmpegResampleSource source = new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES, failing)) {
            Thread.sleep(100);
            IOException failure = assertThrows(IOException.class, () -> FlacFixtures.readAll(source));
            assertTrue(failure.getMessage().contains("Invalid data found"), failure.getMessage());
            assertTrue(failure.getMessage().contains("1"), failure.getMessage());
        }
    }

    @Test
    void launchFailurePropagatesAsIoException() {
        FfmpegResampleSource.Launcher failing = command -> {
            throw new IOException("no ffmpeg");
        };

        try {
            new FfmpegResampleSource("ffmpeg", INPUT, TARGET, TOTAL_FRAMES, failing);
        } catch (IOException expected) {
            assertEquals("no ffmpeg", expected.getMessage());
            return;
        }
        throw new AssertionError("expected IOException");
    }

    @Test
    void realFfmpegResamplesAFixtureAndSeeks() throws Exception {
        assumeTrue(FfmpegLocator.awaitAvailability(), "ffmpeg not available");
        File fixture = FlacFixtures.file(24, false);
        long expectedFrames = 19200;
        try (FfmpegResampleSource source = FfmpegResampleSource.open(FfmpegLocator.executable().orElseThrow(), fixture,
                TARGET, expectedFrames)) {
            long frames = FlacFixtures.readAll(source).length / TARGET.getFrameSize();
            assertTrue(Math.abs(frames - expectedFrames) < 600, "frames " + frames);

            source.seekToFrame(expectedFrames / 2);
            long remaining = FlacFixtures.readAll(source).length / TARGET.getFrameSize();
            assertTrue(Math.abs(remaining - expectedFrames / 2) < 600, "remaining " + remaining);
        }
    }
}
