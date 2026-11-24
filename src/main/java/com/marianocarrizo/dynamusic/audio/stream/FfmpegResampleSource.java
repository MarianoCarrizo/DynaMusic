package com.marianocarrizo.dynamusic.audio.stream;

import com.marianocarrizo.dynamusic.audio.pcm.FfmpegProcess;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;

public final class FfmpegResampleSource implements PcmSource {

    @FunctionalInterface
    public interface Launcher {
        Process start(List<String> command) throws IOException;
    }

    private static final String CODEC = "pcm_s16le";
    private static final String RAW_FORMAT = "s16le";

    private final String executable;
    private final File file;
    private final AudioFormat format;
    private final long totalFrames;
    private final Launcher launcher;
    private final int frameBytes;
    private Process process;
    private InputStream output;

    public FfmpegResampleSource(String executable, File file, AudioFormat format, long totalFrames,
            Launcher launcher) throws IOException {
        this.executable = executable;
        this.file = file;
        this.format = format;
        this.totalFrames = totalFrames;
        this.launcher = launcher;
        this.frameBytes = format.getFrameSize();
        launchAt(0);
    }

    public static FfmpegResampleSource open(String executable, File file, AudioFormat format, long totalFrames)
            throws IOException {
        return new FfmpegResampleSource(executable, file, format, totalFrames, FfmpegProcess::start);
    }

    static List<String> arguments(String executable, File file, AudioFormat format, double startSeconds) {
        List<String> command = new ArrayList<>(List.of(executable, "-hide_banner", "-nostdin", "-v", "error"));
        if (startSeconds > 0) {
            command.add("-ss");
            command.add(String.format(Locale.ROOT, "%.6f", startSeconds));
        }
        command.addAll(List.of("-i", file.getPath(), "-map", "0:a:0", "-vn", "-sn", "-dn",
                "-ac", String.valueOf(format.getChannels()), "-ar", String.valueOf((int) format.getSampleRate()),
                "-acodec", CODEC, "-f", RAW_FORMAT, "pipe:1"));
        return command;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public long totalFrames() {
        return totalFrames;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        if (output == null) return END_OF_STREAM;
        int aligned = buffer.length - buffer.length % frameBytes;
        int count = output.readNBytes(buffer, 0, aligned);
        int usable = count - count % frameBytes;
        return usable <= 0 ? END_OF_STREAM : usable;
    }

    @Override
    public void seekToFrame(long frame) throws IOException {
        long target = Math.max(0, totalFrames > 0 ? Math.min(frame, totalFrames) : frame);
        releaseProcess();
        launchAt(target / (double) format.getSampleRate());
    }

    @Override
    public void close() {
        releaseProcess();
    }

    private void launchAt(double startSeconds) throws IOException {
        process = launcher.start(arguments(executable, file, format, startSeconds));
        output = process.getInputStream();
    }

    private void releaseProcess() {
        Process current = process;
        process = null;
        output = null;
        if (current == null) return;
        FfmpegProcess.release(current);
        try {
            current.getInputStream().close();
        } catch (IOException alreadyClosed) {
            return;
        }
    }
}
