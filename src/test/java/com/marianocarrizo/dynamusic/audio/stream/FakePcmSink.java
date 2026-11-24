package com.marianocarrizo.dynamusic.audio.stream;

import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;

final class FakePcmSink implements PcmSink {

    private final AudioFormat format;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private long framePosition;
    private int starts;
    private int stops;
    private int flushes;
    private int drains;
    private int closes;

    FakePcmSink(AudioFormat format) {
        this.format = format;
    }

    byte[] bytes() {
        return written.toByteArray();
    }

    int[] samples(SampleWidth width) {
        return PcmBytes.unpack(width, bytes());
    }

    void playFrames(long frames) {
        framePosition += frames;
    }

    int starts() {
        return starts;
    }

    int stops() {
        return stops;
    }

    int flushes() {
        return flushes;
    }

    int drains() {
        return drains;
    }

    int closes() {
        return closes;
    }

    boolean closed() {
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
}
