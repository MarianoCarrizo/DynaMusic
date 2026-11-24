package com.marianocarrizo.dynamusic.audio.wasapi;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class FakeExclusiveStream implements ExclusiveStream {

    private final SampleContainer container;
    private final int periodFrames;
    private final int frameBytes;
    private final ByteArrayOutputStream submittedBytes = new ByteArrayOutputStream();
    private final List<String> calls = new ArrayList<>();
    private long submitted;
    private long consumed;
    private int closes;
    private boolean periodAvailable = true;
    private RuntimeException submitFailure;

    FakeExclusiveStream(SampleContainer container, int periodFrames, int channels) {
        this.container = container;
        this.periodFrames = periodFrames;
        this.frameBytes = channels * container.bytes();
    }

    byte[] submittedBytes() {
        return submittedBytes.toByteArray();
    }

    List<String> calls() {
        return calls;
    }

    int closes() {
        return closes;
    }

    void playFrames(long frames) {
        consumed = Math.min(submitted, consumed + frames);
    }

    void playEverything() {
        consumed = submitted;
    }

    void stopRequestingAudio() {
        periodAvailable = false;
    }

    void failSubmitsWith(RuntimeException failure) {
        submitFailure = failure;
    }

    @Override
    public SampleContainer container() {
        return container;
    }

    @Override
    public int periodFrames() {
        return periodFrames;
    }

    @Override
    public int padding() {
        return (int) (submitted - consumed);
    }

    @Override
    public boolean awaitPeriod(int timeoutMillis) {
        if (!periodAvailable) return false;
        consumed = Math.max(consumed, submitted - periodFrames);
        return true;
    }

    @Override
    public void submit(byte[] containerBytes, int frames) {
        if (submitFailure != null) throw submitFailure;
        submittedBytes.write(containerBytes, 0, frames * frameBytes);
        submitted += frames;
        calls.add("submit");
    }

    @Override
    public void submitSilence(int frames) {
        submitted += frames;
        calls.add("silence");
    }

    @Override
    public void start() {
        calls.add("start");
    }

    @Override
    public void stop() {
        calls.add("stop");
    }

    @Override
    public void reset() {
        consumed = submitted;
        calls.add("reset");
    }

    @Override
    public void close() {
        closes++;
        calls.add("close");
    }
}
