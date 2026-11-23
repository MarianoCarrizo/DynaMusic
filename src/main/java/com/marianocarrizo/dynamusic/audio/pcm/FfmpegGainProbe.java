package com.marianocarrizo.dynamusic.audio.pcm;

import com.marianocarrizo.dynamusic.service.FfmpegLocator;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class FfmpegGainProbe {

    static final long TIMEOUT_SECONDS = 60;
    private static final int PROBE_BITS = 16;
    private static final int BUFFER_BYTES = 16 * 1024;

    private FfmpegGainProbe() {}

    public static double measure(File source) throws IOException, InterruptedException {
        String executable = FfmpegLocator.executable().orElseThrow(() -> new IOException("ffmpeg not found"));
        GainMeter meter = new GainMeter(PROBE_BITS, true, false);
        FfmpegProcess.run(arguments(executable, source), TIMEOUT_SECONDS, stdout -> {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read = stdout.read(buffer);
            while (read != -1) {
                meter.accept(buffer, read);
                read = stdout.read(buffer);
            }
        });
        return meter.gain();
    }

    static List<String> arguments(String executable, File source) {
        return List.of(executable, "-hide_banner", "-nostdin", "-v", "error",
                "-i", source.getPath(), "-map", "0:a:0", "-vn", "-ac", "2", "-ar", "22050", "-f", "s16le", "-");
    }
}
