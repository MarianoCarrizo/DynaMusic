package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

final class ReducedDepthSink implements PcmSink {

    private static final int WIDE_BYTES = 3;
    private static final int NARROW_BYTES = 2;

    private final AudioFormat sourceFormat;
    private final PcmSink line;
    private byte[] widened = new byte[0];
    private byte[] narrowed = new byte[0];

    ReducedDepthSink(AudioFormat sourceFormat, PcmSink line) {
        this.sourceFormat = sourceFormat;
        this.line = line;
    }

    @Override
    public AudioFormat format() {
        return sourceFormat;
    }

    @Override
    public OutputStatus status() {
        return line.status();
    }

    @Override
    public void start() {
        line.start();
    }

    @Override
    public void stop() {
        line.stop();
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        int samples = length / WIDE_BYTES;
        int wideLength = samples * WIDE_BYTES;
        if (widened.length < wideLength) widened = new byte[wideLength];
        if (narrowed.length < samples * NARROW_BYTES) narrowed = new byte[samples * NARROW_BYTES];
        System.arraycopy(data, offset, widened, 0, wideLength);
        int reduced = SampleDepth.reduceToSixteen(widened, wideLength, narrowed);
        int accepted = line.write(narrowed, 0, reduced);
        return accepted / NARROW_BYTES * WIDE_BYTES;
    }

    @Override
    public void flush() {
        line.flush();
    }

    @Override
    public void drain() {
        line.drain();
    }

    @Override
    public long framePosition() {
        return line.framePosition();
    }

    @Override
    public void close() {
        line.close();
    }
}
