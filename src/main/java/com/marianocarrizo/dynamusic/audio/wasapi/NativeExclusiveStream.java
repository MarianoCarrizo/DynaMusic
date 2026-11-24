package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import java.lang.foreign.MemorySegment;
import javax.sound.sampled.AudioFormat;

final class NativeExclusiveStream implements ExclusiveStream {

    private static final int BITS_PER_BYTE = 8;

    private final Endpoint endpoint;
    private final AudioClient client;
    private final RenderClient render;
    private final Win32Event event;
    private final ProAudioTask task;
    private final SampleContainer container;
    private final int periodFrames;
    private final int frameBytes;
    private boolean closed;

    private NativeExclusiveStream(Endpoint endpoint, AudioClient client, RenderClient render, Win32Event event,
            ProAudioTask task, SampleContainer container, int periodFrames, int channels) {
        this.endpoint = endpoint;
        this.client = client;
        this.render = render;
        this.event = event;
        this.task = task;
        this.container = container;
        this.periodFrames = periodFrames;
        this.frameBytes = channels * container.containerBits() / BITS_PER_BYTE;
    }

    static NativeExclusiveStream open(AudioFormat format, long requestedHns) {
        Endpoint endpoint = Endpoint.open();
        try {
            return create(endpoint, format, requestedHns);
        } catch (RuntimeException failure) {
            endpoint.close();
            throw failure;
        }
    }

    private static NativeExclusiveStream create(Endpoint endpoint, AudioFormat format, long requestedHns) {
        int rate = Math.round(format.getSampleRate());
        int channels = format.getChannels();
        AudioClient probe = endpoint.activateClient();
        SampleContainer container;
        long periodHns;
        try {
            container = SampleContainer.choose(format.getSampleSizeInBits(),
                    candidate -> Hresult.of(probe.isExclusiveSupported(
                            WaveFormat.create(endpoint.arena(), candidate, rate, channels))) == Hresult.S_OK)
                    .orElseThrow(() -> new OutputUnavailableException("No exclusive format is accepted"));
            periodHns = WasapiPeriod.target(requestedHns, probe.minimumPeriodHns());
        } finally {
            probe.release();
        }
        MemorySegment waveFormat = WaveFormat.create(endpoint.arena(), container, rate, channels);
        AudioClient client = initialized(endpoint, waveFormat, rate, periodHns);
        Win32Event event = null;
        RenderClient render = null;
        try {
            event = Win32Event.create();
            client.setEventHandle(event);
            render = client.renderClient();
            ProAudioTask task = ProAudioTask.enter(endpoint.arena());
            return new NativeExclusiveStream(endpoint, client, render, event, task, container,
                    client.bufferFrames(), channels);
        } catch (RuntimeException failure) {
            if (render != null) render.release();
            client.release();
            if (event != null) event.close();
            throw failure;
        }
    }

    private static AudioClient initialized(Endpoint endpoint, MemorySegment waveFormat, int rate, long periodHns) {
        AudioClient client = endpoint.activateClient();
        int code = client.initializeExclusive(periodHns, waveFormat);
        if (Hresult.of(code) != Hresult.AUDCLNT_E_BUFFER_SIZE_NOT_ALIGNED) return verified(client, code);
        int alignedFrames;
        try {
            alignedFrames = client.bufferFrames();
        } finally {
            client.release();
        }
        AudioClient retry = endpoint.activateClient();
        return verified(retry, retry.initializeExclusive(WasapiPeriod.aligned(alignedFrames, rate), waveFormat));
    }

    private static AudioClient verified(AudioClient client, int code) {
        if (Hresult.failed(code)) client.release();
        Hresult.check("Initialize", code);
        return client;
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
        return client.padding();
    }

    @Override
    public boolean awaitPeriod(int timeoutMillis) {
        return event.await(timeoutMillis);
    }

    @Override
    public void submit(byte[] containerBytes, int frames) {
        render.write(containerBytes, frames, frames * frameBytes);
    }

    @Override
    public void submitSilence(int frames) {
        render.writeSilence(frames);
    }

    @Override
    public void start() {
        client.start();
    }

    @Override
    public void stop() {
        client.stop();
    }

    @Override
    public void reset() {
        client.reset();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        attempt(client::stop);
        attempt(client::reset);
        render.release();
        client.release();
        event.close();
        task.leave();
        endpoint.close();
    }

    private static void attempt(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ignored) {
            return;
        }
    }
}
