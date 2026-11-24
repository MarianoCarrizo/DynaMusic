package com.marianocarrizo.dynamusic.audio.wasapi;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class FakeSharedStream implements SharedStream {

    private final int bufferFrames;
    private final int framesPerEvent;
    private final ByteArrayOutputStream submittedBytes = new ByteArrayOutputStream();
    private final List<String> calls = new ArrayList<>();
    private long submitted;
    private long consumed;
    private int closes;
    private boolean eventsFlow = true;
    private RuntimeException submitFailure;
    private int submitsBeforeFailure;
    private int submits;

    FakeSharedStream(int bufferFrames, int framesPerEvent) {
        this.bufferFrames = bufferFrames;
        this.framesPerEvent = framesPerEvent;
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

    long submittedFrames() {
        return submitted;
    }

    void stopSignalling() {
        eventsFlow = false;
    }

    void failSubmitsAfter(int goodSubmits, RuntimeException failure) {
        submitsBeforeFailure = goodSubmits;
        submitFailure = failure;
    }

    @Override
    public int bufferFrames() {
        return bufferFrames;
    }

    @Override
    public int padding() {
        return (int) (submitted - consumed);
    }

    @Override
    public boolean awaitSpace(int timeoutMillis) {
        if (!eventsFlow) return false;
        consumed = Math.min(submitted, consumed + framesPerEvent);
        return true;
    }

    @Override
    public void submit(byte[] floatFrames, int frames) {
        if (submitFailure != null && submits >= submitsBeforeFailure) throw submitFailure;
        submits++;
        submittedBytes.write(floatFrames, 0, frames * FloatPcm.FRAME_BYTES);
        submitted += frames;
        calls.add("submit");
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
