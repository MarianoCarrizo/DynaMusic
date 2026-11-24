package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

final class FakeExclusiveSink implements PcmSink {

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

    FakeExclusiveSink(AudioFormat format) {
        this.format = format;
    }

    void failStartWith(RuntimeException failure) {
        startFailure = failure;
    }

    void failWritesWith(RuntimeException failure) {
        writeFailure = failure;
    }

    void failSuspendWith(RuntimeException failure) {
        suspendFailure = failure;
    }

    void failStopWith(RuntimeException failure) {
        stopFailure = failure;
    }

    void playFrames(long frames) {
        framePosition += frames;
    }

    int starts() {
        return starts;
    }

    int writes() {
        return writes;
    }

    int suspends() {
        return suspends;
    }

    int closes() {
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
        return length;
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
