package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputRoute;
import com.marianocarrizo.dynamusic.audio.stream.OutputStatus;
import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import com.marianocarrizo.dynamusic.audio.stream.PcmSink;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;

public final class WasapiSharedSink implements PcmSink {

    private static final int AWAIT_MILLIS = 2000;
    private static final int DRAIN_POLL_MILLIS = 50;
    private static final int DRAIN_SLACK_MILLIS = 250;
    private static final int MILLIS_PER_SECOND = 1000;

    private final WasapiSharedApi api;
    private final AudioFormat format;
    private final DevicePosition position = new DevicePosition();
    private SharedStream stream;
    private byte[] staging = new byte[0];
    private int bufferFrames;
    private boolean started;
    private boolean closed;

    public WasapiSharedSink(WasapiSharedApi api, AudioFormat format) {
        this.api = api;
        this.format = format;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public OutputStatus status() {
        return OutputStatus.of(OutputRoute.WASAPI_SHARED, format);
    }

    @Override
    public void start() {
        if (closed) throw new IllegalStateException("Sink is closed");
        if (started) return;
        SharedStream live = acquire();
        try {
            position.begin(0);
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
        int done = 0;
        try {
            while (done < frames) {
                int chunk = Math.min(awaitFreeFrames(), frames - done);
                submit(data, offset + done * sourceFrameBytes, chunk);
                done += chunk;
            }
        } catch (OutputUnavailableException failure) {
            throw failure.consuming(done * sourceFrameBytes);
        }
        return frames * sourceFrameBytes;
    }

    @Override
    public void flush() {
        if (stream == null) return;
        stop();
        position.discard(stream.padding());
        stream.reset();
    }

    @Override
    public void drain() {
        if (!started) return;
        long millis = (long) bufferFrames * MILLIS_PER_SECOND / Math.round(format.getSampleRate())
                + DRAIN_SLACK_MILLIS;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        int padding = stream.padding();
        while (padding > 0 && System.nanoTime() < deadline) {
            stream.awaitSpace(DRAIN_POLL_MILLIS);
            padding = stream.padding();
            position.sample(padding);
        }
    }

    @Override
    public long framePosition() {
        return position.position();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        releaseStream();
    }

    private SharedStream acquire() {
        if (stream != null) return stream;
        SharedStream opened = api.openShared(format);
        stream = opened;
        bufferFrames = opened.bufferFrames();
        int stagingBytes = bufferFrames * FloatPcm.FRAME_BYTES;
        if (staging.length != stagingBytes) staging = new byte[stagingBytes];
        return opened;
    }

    private int awaitFreeFrames() {
        int padding = stream.padding();
        position.sample(padding);
        while (padding >= bufferFrames) {
            if (!stream.awaitSpace(AWAIT_MILLIS)) {
                throw new OutputUnavailableException("The shared device stopped requesting audio");
            }
            padding = stream.padding();
            position.sample(padding);
        }
        return bufferFrames - padding;
    }

    private void submit(byte[] data, int offset, int frames) {
        FloatPcm.convert(data, offset, format.getSampleSizeInBits(), format.getChannels(), frames, staging);
        stream.submit(staging, frames);
        position.submit(frames);
        position.accept(frames);
    }

    private void releaseStream() {
        started = false;
        SharedStream released = stream;
        stream = null;
        if (released != null) released.close();
    }
}
