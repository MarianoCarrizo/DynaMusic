package com.marianocarrizo.dynamusic.audio.pcm;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class FfmpegConverter implements PcmConverter {

    public static final int MAX_SAMPLE_RATE = 96000;
    static final long TIMEOUT_SECONDS = 180;
    static final String PCM_16 = "pcm_s16le";
    static final String PCM_24 = "pcm_s24le";
    private static final List<Integer> ALLOWED_SAMPLE_RATES =
            List.of(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000, 88200, MAX_SAMPLE_RATE);
    private static final long MIN_WAV_BYTES = 45;

    @Override
    public void convert(File source, Path targetWav) throws IOException, InterruptedException {
        String executable = FfmpegLocator.executable().orElseThrow(() -> new IOException("ffmpeg not found"));
        try {
            FfmpegProcess.run(arguments(executable, source, targetWav), TIMEOUT_SECONDS, FfmpegProcess.DISCARD);
            if (!Files.isRegularFile(targetWav) || Files.size(targetWav) < MIN_WAV_BYTES) {
                throw new IOException("ffmpeg produced no audio");
            }
        } catch (IOException | InterruptedException | RuntimeException failure) {
            Files.deleteIfExists(targetWav);
            throw failure;
        }
    }

    static List<String> arguments(String executable, File source, Path targetWav) {
        return List.of(executable, "-hide_banner", "-nostdin", "-v", "error", "-y",
                "-i", source.getPath(), "-map", "0:a:0", "-vn", "-sn", "-dn", "-map_metadata", "-1",
                "-fflags", "+bitexact", "-af", sampleRateFilter(), "-c:a", codecFor(source),
                "-f", AudioFormat.WAV.primaryExtension(), targetWav.toString());
    }

    static String codecFor(File source) {
        return AudioFormat.APE.matches(source) ? PCM_24 : PCM_16;
    }

    static String sampleRateFilter() {
        return "aformat=sample_rates=" + ALLOWED_SAMPLE_RATES.stream().map(String::valueOf)
                .collect(Collectors.joining("|"));
    }
}
