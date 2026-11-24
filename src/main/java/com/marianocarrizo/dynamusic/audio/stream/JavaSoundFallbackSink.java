package com.marianocarrizo.dynamusic.audio.stream;

import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;

public final class JavaSoundFallbackSink implements PcmSink {

    private final PcmSink primary;
    private final SharedLines lines;
    private PcmSink active;
    private long positionBase;
    private boolean started;

    public JavaSoundFallbackSink(PcmSink primary, SharedLines lines) {
        this.primary = primary;
        this.lines = lines;
        this.active = primary;
    }

    @Override
    public AudioFormat format() {
        return primary.format();
    }

    @Override
    public OutputStatus status() {
        return active.status();
    }

    @Override
    public void start() {
        if (started) return;
        try {
            active.start();
        } catch (OutputUnavailableException unavailable) {
            if (active != primary) throw unavailable;
            fallBack(true);
        }
        started = true;
    }

    @Override
    public void stop() {
        started = false;
        guarded(PcmSink::stop);
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        if (active != primary) return active.write(data, offset, length);
        try {
            return primary.write(data, offset, length);
        } catch (OutputUnavailableException unavailable) {
            fallBack(started);
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
        guarded(PcmSink::suspend);
    }

    @Override
    public long framePosition() {
        return positionBase + active.framePosition();
    }

    @Override
    public void close() {
        started = false;
        try {
            primary.close();
        } finally {
            if (active != primary) active.close();
        }
    }

    private void guarded(Consumer<PcmSink> action) {
        try {
            action.accept(active);
        } catch (OutputUnavailableException unavailable) {
            if (active != primary) throw unavailable;
            fallBack(started);
        }
    }

    private void fallBack(boolean startActive) {
        positionBase = primary.framePosition();
        primary.close();
        active = JavaSoundOpening.open(lines, primary.format());
        if (startActive) active.start();
    }
}
