package com.marianocarrizo.dynamusic.audio.stream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class FakeFfmpegProcess extends Process {

    private final InputStream stdout;
    private final int exit;
    private final byte[] stderr;
    private boolean destroyed;

    FakeFfmpegProcess(byte[] output) {
        this(output, 0, "");
    }

    FakeFfmpegProcess(byte[] output, int exit, String stderr) {
        this.stdout = new ByteArrayInputStream(output);
        this.exit = exit;
        this.stderr = stderr.getBytes(StandardCharsets.UTF_8);
    }

    boolean destroyed() {
        return destroyed;
    }

    @Override
    public OutputStream getOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return stdout;
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(stderr);
    }

    @Override
    public int waitFor() {
        return exitValue();
    }

    @Override
    public int exitValue() {
        return exit;
    }

    @Override
    public void destroy() {
        destroyed = true;
    }
}
