package com.marianocarrizo.dynamusic.audio.stream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class FakeFfmpegProcess extends Process {

    private final InputStream stdout;
    private boolean destroyed;

    FakeFfmpegProcess(byte[] output) {
        this.stdout = new ByteArrayInputStream(output);
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
        return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
        return exitValue();
    }

    @Override
    public int exitValue() {
        return 0;
    }

    @Override
    public void destroy() {
        destroyed = true;
    }
}
