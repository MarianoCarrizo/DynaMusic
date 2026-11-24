package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import java.lang.foreign.MemorySegment;
import javax.sound.sampled.AudioFormat;

final class NativeSharedStream implements SharedStream {

    private final Endpoint endpoint;
    private final AudioClient client;
    private final RenderClient render;
    private final Win32Event event;
    private final ProAudioTask task;
    private final int bufferFrames;
    private boolean closed;

    private NativeSharedStream(Endpoint endpoint, AudioClient client, RenderClient render, Win32Event event,
            ProAudioTask task, int bufferFrames) {
        this.endpoint = endpoint;
        this.client = client;
        this.render = render;
        this.event = event;
        this.task = task;
        this.bufferFrames = bufferFrames;
    }

    static boolean supports(AudioFormat format) {
        try (Endpoint endpoint = Endpoint.open()) {
            AudioClient client = endpoint.activateClient();
            try {
                return client.mixChannels() == FloatPcm.OUTPUT_CHANNELS
                        && !Hresult.failed(client.initializeShared(floatFormat(endpoint, format)));
            } finally {
                client.release();
            }
        }
    }

    static NativeSharedStream open(AudioFormat format) {
        Endpoint endpoint = Endpoint.open();
        try {
            return create(endpoint, format);
        } catch (RuntimeException failure) {
            endpoint.close();
            throw failure;
        }
    }

    private static NativeSharedStream create(Endpoint endpoint, AudioFormat format) {
        AudioClient client = endpoint.activateClient();
        Win32Event event = null;
        RenderClient render = null;
        try {
            if (client.mixChannels() != FloatPcm.OUTPUT_CHANNELS) {
                throw new OutputUnavailableException("The shared mix format is not stereo");
            }
            Hresult.check("Initialize", client.initializeShared(floatFormat(endpoint, format)));
            event = Win32Event.create();
            client.setEventHandle(event);
            render = client.renderClient();
            int bufferFrames = client.bufferFrames();
            ProAudioTask task = ProAudioTask.enter(endpoint.arena());
            return new NativeSharedStream(endpoint, client, render, event, task, bufferFrames);
        } catch (RuntimeException failure) {
            if (render != null) render.release();
            client.release();
            if (event != null) event.close();
            throw failure;
        }
    }

    private static MemorySegment floatFormat(Endpoint endpoint, AudioFormat format) {
        return WaveFormat.createFloat(endpoint.arena(), Math.round(format.getSampleRate()),
                FloatPcm.OUTPUT_CHANNELS);
    }

    @Override
    public int bufferFrames() {
        return bufferFrames;
    }

    @Override
    public int padding() {
        return client.padding();
    }

    @Override
    public boolean awaitSpace(int timeoutMillis) {
        return event.await(timeoutMillis);
    }

    @Override
    public void submit(byte[] floatFrames, int frames) {
        render.write(floatFrames, frames, frames * FloatPcm.FRAME_BYTES);
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
