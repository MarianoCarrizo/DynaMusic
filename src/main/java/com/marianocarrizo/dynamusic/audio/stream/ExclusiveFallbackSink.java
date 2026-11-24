package com.marianocarrizo.dynamusic.audio.stream;

import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

public final class ExclusiveFallbackSink implements PcmSink {

    public static final long DEFAULT_CLAIM_MILLIS = 1000;
    private static final int REDUCED_BITS = 16;

    private final PcmSink exclusive;
    private final SharedLines lines;
    private final OutputArbiter arbiter;
    private final long claimMillis;
    private PcmSink active;
    private long positionBase;
    private boolean started;

    public ExclusiveFallbackSink(PcmSink exclusive, SharedLines lines, OutputArbiter arbiter) {
        this(exclusive, lines, arbiter, DEFAULT_CLAIM_MILLIS);
    }

    public ExclusiveFallbackSink(PcmSink exclusive, SharedLines lines, OutputArbiter arbiter, long claimMillis) {
        this.exclusive = exclusive;
        this.lines = lines;
        this.arbiter = arbiter;
        this.claimMillis = claimMillis;
        this.active = exclusive;
    }

    @Override
    public AudioFormat format() {
        return exclusive.format();
    }

    @Override
    public OutputStatus status() {
        return active.status();
    }

    @Override
    public void start() {
        if (started) return;
        started = true;
        if (active == exclusive) startExclusive();
        else active.start();
    }

    @Override
    public void stop() {
        started = false;
        guarded(PcmSink::stop);
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        if (active == exclusive && arbiter.sharedInUse()) fallBack();
        if (active != exclusive) return active.write(data, offset, length);
        try {
            return exclusive.write(data, offset, length);
        } catch (OutputUnavailableException unavailable) {
            fallBack();
            int consumed = Math.min(Math.max(unavailable.consumedBytes(), 0), length);
            return consumed + active.write(data, offset + consumed, length - consumed);
        }
    }

    @Override
    public void flush() {
        guarded(PcmSink::flush);
    }

    @Override
    public void drain() {
        guarded(PcmSink::drain);
    }

    @Override
    public void suspend() {
        started = false;
        if (active != exclusive) return;
        try {
            exclusive.suspend();
            arbiter.releaseExclusive(this);
        } catch (OutputUnavailableException unavailable) {
            fallBack();
        }
    }

    @Override
    public long framePosition() {
        return positionBase + active.framePosition();
    }

    @Override
    public void close() {
        started = false;
        exclusive.close();
        if (active != exclusive) active.close();
        arbiter.releaseExclusive(this);
    }

    private void startExclusive() {
        if (!arbiter.claimExclusive(this, claimMillis)) {
            fallBack();
            return;
        }
        try {
            exclusive.start();
        } catch (OutputUnavailableException unavailable) {
            fallBack();
            return;
        }
        if (arbiter.sharedInUse()) fallBack();
    }

    private void guarded(Consumer<PcmSink> action) {
        try {
            action.accept(active);
        } catch (OutputUnavailableException unavailable) {
            if (active != exclusive) throw unavailable;
            fallBack();
        }
    }

    private void fallBack() {
        retireExclusive();
        positionBase = exclusive.framePosition();
        active = openShared();
        if (started) active.start();
    }

    private void retireExclusive() {
        try {
            exclusive.suspend();
        } catch (OutputUnavailableException unavailable) {
            exclusive.close();
        }
        arbiter.releaseExclusive(this);
    }

    private PcmSink openShared() {
        AudioFormat source = exclusive.format();
        try {
            boolean reduces = source.getSampleSizeInBits() > REDUCED_BITS && !lines.supports(source);
            if (!reduces) return lines.open(source);
            return new ReducedDepthSink(source, lines.open(PcmFormats.withBits(source, REDUCED_BITS)));
        } catch (LineUnavailableException unavailable) {
            throw new OutputUnavailableException("No shared output is available", unavailable);
        }
    }
}
