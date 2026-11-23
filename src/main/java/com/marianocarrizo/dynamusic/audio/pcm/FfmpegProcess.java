package com.marianocarrizo.dynamusic.audio.pcm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FfmpegProcess {

    static final int STDERR_TAIL_BYTES = 2048;
    private static final Set<Process> LIVE = ConcurrentHashMap.newKeySet();

    @FunctionalInterface
    interface StdoutSink {
        void consume(InputStream stdout) throws IOException;
    }

    static final StdoutSink DISCARD = stdout -> stdout.transferTo(OutputStream.nullOutputStream());

    private FfmpegProcess() {}

    public static void destroyAll() {
        LIVE.forEach(Process::destroyForcibly);
        LIVE.clear();
    }

    static void run(List<String> command, long timeoutSeconds, StdoutSink sink)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        LIVE.add(process);
        TailBuffer stderr = new TailBuffer(STDERR_TAIL_BYTES);
        AtomicReference<IOException> sinkFailure = new AtomicReference<>();
        Thread stderrReader = Thread.ofVirtual().start(() -> drain(process.getErrorStream(), stderr));
        Thread stdoutReader = Thread.ofVirtual().start(() -> consume(process, sink, sinkFailure));
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("ffmpeg timed out after " + timeoutSeconds + " s");
            }
            stdoutReader.join();
            stderrReader.join();
            if (process.exitValue() != 0) {
                throw new IOException("ffmpeg exited with " + process.exitValue() + ": " + stderr.text());
            }
            if (sinkFailure.get() != null) throw sinkFailure.get();
        } finally {
            process.destroyForcibly();
            LIVE.remove(process);
        }
    }

    private static void consume(Process process, StdoutSink sink, AtomicReference<IOException> failure) {
        try {
            sink.consume(process.getInputStream());
        } catch (IOException e) {
            failure.set(e);
        }
    }

    private static void drain(InputStream stream, TailBuffer tail) {
        byte[] buffer = new byte[STDERR_TAIL_BYTES];
        try {
            int read = stream.read(buffer);
            while (read != -1) {
                tail.append(buffer, read);
                read = stream.read(buffer);
            }
        } catch (IOException closed) {
            return;
        }
    }

    static final class TailBuffer {
        private final int capacity;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        TailBuffer(int capacity) {
            this.capacity = capacity;
        }

        synchronized void append(byte[] buffer, int length) {
            bytes.write(buffer, 0, length);
            if (bytes.size() > capacity * 2) {
                byte[] all = bytes.toByteArray();
                bytes.reset();
                bytes.write(all, all.length - capacity, capacity);
            }
        }

        synchronized String text() {
            byte[] all = bytes.toByteArray();
            int start = Math.max(0, all.length - capacity);
            return new String(all, start, all.length - start, StandardCharsets.UTF_8).trim();
        }
    }
}
