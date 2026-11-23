package com.marianocarrizo.dynamusic.audio.pcm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.marianocarrizo.dynamusic.service.FfmpegLocator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegPcmTest {

    private static final File FLAC_24 = Paths.get("src", "test", "resources", "audio", "tone-24bit.flac").toFile();

    @TempDir Path dir;

    private static void requireFfmpeg() {
        assumeTrue(FfmpegLocator.isAvailable(), "ffmpeg not available");
    }

    private static float sampleRateOf(Path wav) throws Exception {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(wav.toFile())) {
            return stream.getFormat().getSampleRate();
        }
    }

    private Path highRateWav(int rate) throws Exception {
        byte[] silence = new byte[rate * 2 / 5];
        AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
        Path wav = dir.resolve("hires" + rate + ".wav");
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(silence), format,
                silence.length / 2)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        return wav;
    }

    @Test
    void argumentsDropMetadataVideoAndSelectOneAudioStream() {
        List<String> args = FfmpegConverter.arguments("ffmpeg", new File("a.ogg"), Path.of("out.wav"));

        assertTrue(args.containsAll(List.of("-vn", "-sn", "-dn", "-map_metadata", "-1", "-nostdin")));
        assertEquals("0:a:0", args.get(args.indexOf("-map") + 1));
        assertFalse(args.contains("-ac"));
    }

    @Test
    void lossyAndLosslessSourcesGetTheirOutputDepth() {
        assertEquals(FfmpegConverter.PCM_16, FfmpegConverter.codecFor(new File("a.opus")));
        assertEquals(FfmpegConverter.PCM_24, FfmpegConverter.codecFor(new File("a.ape")));
    }

    @Test
    void sampleRateFilterCapsAtNinetySixKilohertz() {
        String filter = FfmpegConverter.sampleRateFilter();

        assertTrue(filter.endsWith("|" + FfmpegConverter.MAX_SAMPLE_RATE));
        assertFalse(filter.contains("192000"));
    }

    @Test
    void gainProbeArgumentsDownmixOnlyForMeasuring() {
        List<String> args = FfmpegGainProbe.arguments("ffmpeg", new File("a.mp3"));

        assertEquals("2", args.get(args.indexOf("-ac") + 1));
        assertEquals("-", args.get(args.size() - 1));
    }

    @Test
    void convertsFlacToReadableWav() throws Exception {
        requireFfmpeg();
        Path target = dir.resolve("out.wav");

        new FfmpegConverter().convert(FLAC_24, target);

        assertEquals(44100f, sampleRateOf(target));
        assertTrue(AudioFileGain.measure(target.toFile()) <= 1.0);
    }

    @Test
    void resamplesRatesAboveNinetySixKilohertz() throws Exception {
        requireFfmpeg();
        Path source = highRateWav(192000);
        Path target = dir.resolve("out.wav");

        new FfmpegConverter().convert(source.toFile(), target);

        assertEquals(96000f, sampleRateOf(target));
    }

    @Test
    void keepsRatesAtOrBelowNinetySixKilohertz() throws Exception {
        requireFfmpeg();
        Path source = highRateWav(48000);
        Path target = dir.resolve("out.wav");

        new FfmpegConverter().convert(source.toFile(), target);

        assertEquals(48000f, sampleRateOf(target));
    }

    @Test
    void corruptInputFailsAndLeavesNoOutput() throws Exception {
        requireFfmpeg();
        Path corrupt = Files.write(dir.resolve("bad.opus"), new byte[2048]);
        Path target = dir.resolve("out.wav");

        assertThrows(IOException.class, () -> new FfmpegConverter().convert(corrupt.toFile(), target));
        assertFalse(Files.exists(target));
    }

    @Test
    void gainProbeMatchesTheWavMeasurement() throws Exception {
        requireFfmpeg();
        Path wav = dir.resolve("out.wav");
        new FfmpegConverter().convert(FLAC_24, wav);

        double probed = FfmpegGainProbe.measure(FLAC_24);

        assertEquals(AudioFileGain.measure(wav.toFile()), probed, 0.05);
    }

    @Test
    void timeoutDestroysTheProcess() {
        requireFfmpeg();
        String executable = FfmpegLocator.executable().orElseThrow();
        List<String> forever = List.of(executable, "-hide_banner", "-nostdin", "-v", "error", "-re", "-f", "lavfi",
                "-i", "anullsrc", "-t", "60", "-f", "null", "-");

        IOException failure = assertThrows(IOException.class,
                () -> FfmpegProcess.run(forever, 1, FfmpegProcess.DISCARD));

        assertTrue(failure.getMessage().contains("timed out"));
    }

    @Test
    void stderrTailKeepsOnlyTheLastBytes() {
        FfmpegProcess.TailBuffer tail = new FfmpegProcess.TailBuffer(8);
        byte[] bytes = "0123456789abcdefghij".getBytes();

        tail.append(bytes, bytes.length);

        assertEquals("cdefghij", tail.text());
    }
}
