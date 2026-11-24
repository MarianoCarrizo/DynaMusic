package com.marianocarrizo.dynamusic.audio.stream;

final class PositionClock {

    private final double sourceRate;
    private final double sinkRate;
    private long baseSourceFrame;
    private long markSinkFrame;
    private boolean paused;
    private long frozenSourceFrame;

    PositionClock(double sourceRate, double sinkRate) {
        this.sourceRate = sourceRate;
        this.sinkRate = sinkRate;
    }

    void rebase(long sourceFrame, long sinkFramePosition) {
        baseSourceFrame = sourceFrame;
        markSinkFrame = sinkFramePosition;
        frozenSourceFrame = sourceFrame;
    }

    void pause(long sinkFramePosition) {
        if (paused) return;
        frozenSourceFrame = sourceFrame(sinkFramePosition);
        paused = true;
    }

    void resume(long sinkFramePosition) {
        if (!paused) return;
        rebase(frozenSourceFrame, sinkFramePosition);
        paused = false;
    }

    long sourceFrame(long sinkFramePosition) {
        if (paused) return frozenSourceFrame;
        long elapsedSinkFrames = Math.max(0, sinkFramePosition - markSinkFrame);
        return baseSourceFrame + Math.round(elapsedSinkFrames * sourceRate / sinkRate);
    }

    double seconds(long sinkFramePosition) {
        return sourceFrame(sinkFramePosition) / sourceRate;
    }
}
