package com.marianocarrizo.dynamusic.audio.wasapi;

final class DevicePosition {

    private long submitted;
    private long consumedAtStart;
    private long prerollFrames;
    private long playedBase;
    private long realFrames;
    private volatile long position;

    long position() {
        return position;
    }

    void begin(long prerollFrames) {
        consumedAtStart = submitted;
        this.prerollFrames = prerollFrames;
    }

    void submit(long frames) {
        submitted += frames;
    }

    void accept(long realFramesAdded) {
        realFrames += realFramesAdded;
    }

    void sample(long padding) {
        long consumed = submitted - padding - consumedAtStart - prerollFrames;
        long played = Math.min(realFrames, playedBase + Math.max(0, consumed));
        position = Math.max(position, played);
    }

    void discard(long padding) {
        submitted -= Math.min(padding, submitted);
        sample(0);
        playedBase = position;
        realFrames = position;
        consumedAtStart = submitted;
        prerollFrames = 0;
    }
}
