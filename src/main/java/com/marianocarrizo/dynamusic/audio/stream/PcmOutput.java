package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

public final class PcmOutput {

    private final PcmSink sink;
    private final SampleWidth inputWidth;
    private final boolean reducesDepth;
    private final int inputChannels;
    private final int inputFrameBytes;
    private final int sinkFrameBytes;
    private final GainRamp gain;
    private final PositionClock clock;
    private byte[] scratch = new byte[0];

    public PcmOutput(PcmSink sink, AudioFormat inputFormat, double sourceRate, double volume) {
        this.sink = sink;
        this.inputWidth = SampleWidth.ofBits(inputFormat.getSampleSizeInBits());
        this.reducesDepth = inputFormat.getSampleSizeInBits() > sink.format().getSampleSizeInBits();
        this.inputChannels = inputFormat.getChannels();
        this.inputFrameBytes = inputFormat.getFrameSize();
        this.sinkFrameBytes = sink.format().getFrameSize();
        this.gain = new GainRamp(sink.format().getSampleRate(), volume);
        this.clock = new PositionClock(sourceRate, sink.format().getSampleRate());
        clock.rebase(0, sink.framePosition());
    }

    public void setVolume(double volume) {
        gain.setTarget(volume);
    }

    public int write(byte[] data, int length) {
        int usable = length - length % inputFrameBytes;
        gain.apply(data, 0, usable, inputWidth, inputChannels);
        if (!reducesDepth) return writeFully(data, usable) / sinkFrameBytes;
        if (scratch.length < usable) scratch = new byte[usable];
        int reduced = SampleDepth.reduceToSixteen(data, usable, scratch);
        return writeFully(scratch, reduced) / sinkFrameBytes;
    }

    public void start() {
        clock.resume(sink.framePosition());
        sink.start();
    }

    public long pause() {
        sink.stop();
        long position = sink.framePosition();
        clock.pause(position);
        long playedFrame = clock.sourceFrame(position);
        sink.flush();
        clock.rebase(playedFrame, position);
        gain.restartFromSilence();
        sink.suspend();
        return playedFrame;
    }

    public void seek(long sourceFrame) {
        sink.stop();
        sink.flush();
        clock.rebase(sourceFrame, sink.framePosition());
        gain.restartFromSilence();
    }

    public void release() {
        sink.suspend();
    }

    public OutputStatus status() {
        return sink.status();
    }

    public void drain() {
        sink.drain();
    }

    public long sourceFrame() {
        return clock.sourceFrame(sink.framePosition());
    }

    public double seconds() {
        return clock.seconds(sink.framePosition());
    }

    public void close() {
        sink.close();
    }

    private int writeFully(byte[] data, int length) {
        int written = 0;
        while (written < length) {
            int accepted = sink.write(data, written, length - written);
            if (accepted <= 0) break;
            written += accepted;
        }
        return written;
    }
}
