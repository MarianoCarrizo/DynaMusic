package com.marianocarrizo.dynamusic.audio.stream;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.AudioFormat;

public final class FakePcmSink implements PcmSink {

    private final AudioFormat format;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private volatile long framePosition;
    private volatile int starts;
    private volatile int stops;
    private volatile int flushes;
    private volatile int drains;
    private volatile int closes;
    private volatile int writeAttempts;
    private volatile CountDownLatch writeGate;

    public FakePcmSink(AudioFormat format) {
        this.format = format;
    }

    public byte[] bytes() {
        return written.toByteArray();
    }

    public int[] samples(SampleWidth width) {
        return PcmBytes.unpack(width, bytes());
    }

    public void blockWrites(CountDownLatch gate) {
        writeGate = gate;
    }

    public int writeAttempts() {
        return writeAttempts;
    }

    public void playFrames(long frames) {
        framePosition += frames;
    }

    public int starts() {
        return starts;
    }

    public int stops() {
        return stops;
    }

    public int flushes() {
        return flushes;
    }

    public int drains() {
        return drains;
    }

    public int closes() {
        return closes;
    }

    public boolean closed() {
        return closes > 0;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public void start() {
        starts++;
    }

    @Override
    public void stop() {
        stops++;
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        writeAttempts++;
        awaitGate();
        written.write(data, offset, length);
        return length;
    }

    @Override
    public void flush() {
        flushes++;
        written.reset();
    }

    @Override
    public void drain() {
        drains++;
    }

    @Override
    public long framePosition() {
        return framePosition;
    }

    @Override
    public void close() {
        closes++;
    }

    private void awaitGate() {
        CountDownLatch gate = writeGate;
        if (gate == null) return;
        try {
            gate.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
