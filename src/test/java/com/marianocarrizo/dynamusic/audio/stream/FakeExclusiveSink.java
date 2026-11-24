package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

public final class FakeExclusiveSink implements PcmSink {

    private final AudioFormat format;
    private long framePosition;
    private int starts;
    private int writes;
    private int suspends;
    private int closes;
    private RuntimeException startFailure;
    private RuntimeException writeFailure;
    private RuntimeException suspendFailure;
    private RuntimeException stopFailure;
    private long paceMillis;

    public FakeExclusiveSink(AudioFormat format) {
        this.format = format;
    }

    public void failStartWith(RuntimeException failure) {
        startFailure = failure;
    }

    public void failWritesWith(RuntimeException failure) {
        writeFailure = failure;
    }

    public void failSuspendWith(RuntimeException failure) {
        suspendFailure = failure;
    }

    public void failStopWith(RuntimeException failure) {
        stopFailure = failure;
    }

    public void paceWritesBy(long millis) {
        paceMillis = millis;
    }

    public void playFrames(long frames) {
        framePosition += frames;
    }

    public int starts() {
        return starts;
    }

    public int writes() {
        return writes;
    }

    public int suspends() {
        return suspends;
    }

    public int closes() {
        return closes;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public OutputStatus status() {
        return OutputStatus.of(OutputRoute.EXCLUSIVE, format);
    }

    @Override
    public void start() {
        if (startFailure != null) throw startFailure;
        starts++;
    }

    @Override
    public void stop() {
        if (stopFailure != null) throw stopFailure;
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        if (writeFailure != null) throw writeFailure;
        writes++;
        pace();
        return length;
    }

    private void pace() {
        if (paceMillis == 0) return;
        try {
            Thread.sleep(paceMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void drain() {
    }

    @Override
    public void suspend() {
        if (suspendFailure != null) throw suspendFailure;
        suspends++;
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
