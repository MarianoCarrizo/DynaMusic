package com.marianocarrizo.dynamusic.audio.stream;

final class GainRamp {

    static final double RAMP_SECONDS = 0.010;

    private final int rampFrames;
    private volatile double target;
    private double current;
    private double goal;
    private double step;
    private int remaining;

    GainRamp(double sampleRate, double initialGain) {
        this.rampFrames = Math.max(1, (int) Math.round(sampleRate * RAMP_SECONDS));
        this.target = Math.max(0.0, initialGain);
        this.current = target;
        this.goal = target;
    }

    void setTarget(double gain) {
        target = Math.max(0.0, gain);
    }

    void restartFromSilence() {
        current = 0.0;
        beginRamp(target);
    }

    void apply(byte[] data, int offset, int length, SampleWidth width, int channels) {
        double wanted = target;
        if (wanted != goal) beginRamp(wanted);
        if (remaining == 0 && current == 1.0) return;
        int frameBytes = width.bytes() * channels;
        int end = offset + length - length % frameBytes;
        for (int frame = offset; frame < end; frame += frameBytes) {
            advance();
            for (int channel = 0; channel < channels; channel++) {
                int position = frame + channel * width.bytes();
                width.write(data, position, width.clamp(Math.round(width.read(data, position) * current)));
            }
        }
    }

    private void beginRamp(double wanted) {
        goal = wanted;
        remaining = rampFrames;
        step = (wanted - current) / rampFrames;
    }

    private void advance() {
        if (remaining == 0) return;
        current += step;
        remaining--;
        if (remaining == 0) current = goal;
    }
}
