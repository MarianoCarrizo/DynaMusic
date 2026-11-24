package com.marianocarrizo.dynamusic.audio.stream;

import com.marianocarrizo.dynamusic.audio.pcm.FfmpegProcess;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;

public final class FfmpegResampleSource implements PcmSource {

    @FunctionalInterface
    public interface Launcher {
        Process start(List<String> command) throws IOException;
    }

    private static final String CODEC = "pcm_s16le";
    private static final String RAW_FORMAT = "s16le";
    private static final int STDERR_TAIL_BYTES = 2048;
    private static final long EXIT_WAIT_MILLIS = 1500;

    private final String executable;
    private final File file;
    private final AudioFormat format;
    private final long totalFrames;
    private final Launcher launcher;
    private final int frameBytes;
    private Process process;
    private InputStream output;
    private StderrTail stderr;

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
        if (count < aligned) verifyCleanExit();
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

    private void verifyCleanExit() throws IOException {
        Process current = process;
        if (current == null) return;
        try {
            if (!current.waitFor(EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for ffmpeg", interrupted);
        }
        int exit = current.exitValue();
        if (exit != 0) {
            throw new IOException("ffmpeg exited with " + exit + ": " + stderr.text());
        }
    }

    private void launchAt(double startSeconds) throws IOException {
        Process started = launcher.start(arguments(executable, file, format, startSeconds));
        StderrTail tail = new StderrTail();
        Thread.ofVirtual().start(() -> tail.drain(started.getErrorStream()));
        process = started;
        stderr = tail;
        output = started.getInputStream();
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

    private static final class StderrTail {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        void drain(InputStream stream) {
            byte[] buffer = new byte[STDERR_TAIL_BYTES];
            try {
                int read = stream.read(buffer);
                while (read != -1) {
                    append(buffer, read);
                    read = stream.read(buffer);
                }
            } catch (IOException closed) {
                return;
            }
        }

        private synchronized void append(byte[] buffer, int length) {
            bytes.write(buffer, 0, length);
            if (bytes.size() > STDERR_TAIL_BYTES * 2) {
                byte[] all = bytes.toByteArray();
                bytes.reset();
                bytes.write(all, all.length - STDERR_TAIL_BYTES, STDERR_TAIL_BYTES);
            }
        }

        synchronized String text() {
            byte[] all = bytes.toByteArray();
            int start = Math.max(0, all.length - STDERR_TAIL_BYTES);
            return new String(all, start, all.length - start, StandardCharsets.UTF_8).trim();
        }
    }
}
