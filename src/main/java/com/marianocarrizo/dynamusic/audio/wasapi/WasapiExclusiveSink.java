package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import com.marianocarrizo.dynamusic.audio.stream.PcmSink;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;

public final class WasapiExclusiveSink implements PcmSink {

    private static final int AWAIT_MILLIS = 2000;
    private static final int DRAIN_SILENT_PERIODS = 2;
    private static final int BITS_PER_BYTE = 8;

    private final WasapiApi api;
    private final AudioFormat format;
    private final long periodHns;
    private final DevicePosition position = new DevicePosition();
    private ExclusiveStream stream;
    private SampleContainer container;
    private byte[] staging = new byte[0];
    private int periodFrames;
    private int stagedFrames;
    private int stagedFrameBytes;
    private boolean started;
    private boolean closed;

    public WasapiExclusiveSink(WasapiApi api, AudioFormat format) {
        this(api, format, WasapiPeriod.DEFAULT_HNS);
    }

    public WasapiExclusiveSink(WasapiApi api, AudioFormat format, long periodHns) {
        this.api = api;
        this.format = format;
        this.periodHns = periodHns;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public void start() {
        if (closed) throw new IllegalStateException("Sink is closed");
        if (started) return;
        ExclusiveStream live = acquire();
        try {
            position.begin(periodFrames);
            live.submitSilence(periodFrames);
            position.submit(periodFrames);
            live.start();
        } catch (RuntimeException failure) {
            releaseStream();
            throw failure;
        }
        started = true;
    }

    @Override
    public void stop() {
        if (!started) return;
        started = false;
        stream.stop();
        position.sample(stream.padding());
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        if (!started) throw new IllegalStateException("Sink is not started");
        int sourceFrameBytes = format.getFrameSize();
        int frames = length / sourceFrameBytes;
        int sourceBits = format.getSampleSizeInBits();
        int done = 0;
        while (done < frames) {
            int chunk = Math.min(frames - done, periodFrames - stagedFrames);
            container.repack(data, offset + done * sourceFrameBytes, sourceBits, chunk * format.getChannels(),
                    staging, stagedFrames * stagedFrameBytes);
            stagedFrames += chunk;
            done += chunk;
            position.accept(chunk);
            if (stagedFrames == periodFrames) submitStaged();
        }
        return frames * sourceFrameBytes;
    }

    @Override
    public void flush() {
        stagedFrames = 0;
        if (stream == null) return;
        stop();
        position.discard(stream.padding());
        stream.reset();
    }

    @Override
    public void drain() {
        if (!started) return;
        if (stagedFrames > 0) {
            Arrays.fill(staging, stagedFrames * stagedFrameBytes, staging.length, (byte) 0);
            submitStaged();
        }
        for (int i = 0; i < DRAIN_SILENT_PERIODS; i++) {
            submitSilentPeriod();
        }
    }

    @Override
    public long framePosition() {
        return position.position();
    }

    public void suspend() {
        flush();
        releaseStream();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stagedFrames = 0;
        releaseStream();
    }

    private ExclusiveStream acquire() {
        if (stream != null) return stream;
        ExclusiveStream opened = api.open(format, periodHns);
        stream = opened;
        container = opened.container();
        periodFrames = opened.periodFrames();
        stagedFrameBytes = format.getChannels() * container.containerBits() / BITS_PER_BYTE;
        int stagingBytes = periodFrames * stagedFrameBytes;
        if (staging.length != stagingBytes) staging = new byte[stagingBytes];
        stagedFrames = 0;
        return opened;
    }

    private void submitStaged() {
        awaitPeriod();
        position.sample(stream.padding());
        stream.submit(staging, periodFrames);
        position.submit(periodFrames);
        stagedFrames = 0;
    }

    private void submitSilentPeriod() {
        awaitPeriod();
        position.sample(stream.padding());
        stream.submitSilence(periodFrames);
        position.submit(periodFrames);
    }

    private void awaitPeriod() {
        if (!stream.awaitPeriod(AWAIT_MILLIS)) {
            throw new OutputUnavailableException("The exclusive device stopped requesting audio");
        }
    }

    private void releaseStream() {
        started = false;
        ExclusiveStream released = stream;
        stream = null;
        if (released != null) released.close();
    }
}
