package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.PcmOutput;
import com.marianocarrizo.dynamusic.audio.stream.PcmSource;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;

final class StreamWorker implements Runnable {

    static final double BLOCK_SECONDS = 0.020;
    static final double POSITION_STEP_SECONDS = 0.050;
    private static final long NO_SEEK = -1;

    private final PcmSource source;
    private final PcmOutput output;
    private final StreamListener listener;
    private final byte[] buffer;
    private final Object lock = new Object();
    private boolean wantPlaying;
    private boolean closing;
    private long seekFrame = NO_SEEK;
    private int seekGeneration;
    private boolean running;
    private boolean ended;
    private int generation;
    private double postedSeconds;

    StreamWorker(PcmSource source, PcmOutput output, StreamListener listener) {
        this.source = source;
        this.output = output;
        this.listener = listener;
        AudioFormat format = source.format();
        int blockFrames = Math.max(1, (int) Math.round(format.getSampleRate() * BLOCK_SECONDS));
        this.buffer = new byte[blockFrames * format.getFrameSize()];
    }

    void play() {
        synchronized (lock) {
            wantPlaying = true;
            lock.notifyAll();
        }
    }

    void pause() {
        synchronized (lock) {
            wantPlaying = false;
            lock.notifyAll();
        }
    }

    void seek(long frame, int requestGeneration) {
        synchronized (lock) {
            seekFrame = frame;
            seekGeneration = requestGeneration;
            lock.notifyAll();
        }
    }

    void stopAtStart(int requestGeneration) {
        synchronized (lock) {
            wantPlaying = false;
            seekFrame = 0;
            seekGeneration = requestGeneration;
            lock.notifyAll();
        }
    }

    void setVolume(double volume) {
        output.setVolume(volume);
    }

    void close() {
        synchronized (lock) {
            closing = true;
            lock.notifyAll();
        }
    }

    @Override
    public void run() {
        try {
            output.seek(0);
            loop();
        } catch (IOException | RuntimeException failure) {
            if (!isClosing()) listener.failed(failure);
        } finally {
            release();
        }
    }

    private void loop() throws IOException {
        while (true) {
            long seek;
            boolean play;
            synchronized (lock) {
                if (!awaitWork()) return;
                seek = seekFrame;
                seekFrame = NO_SEEK;
                play = wantPlaying;
                if (seek != NO_SEEK) generation = seekGeneration;
            }
            if (seek != NO_SEEK) applySeek(seek);
            if (play != running) switchRunning(play);
            if (play && !ended) pump();
        }
    }

    private boolean awaitWork() {
        try {
            while (!closing && seekFrame == NO_SEEK && wantPlaying == running && (!wantPlaying || ended)) {
                lock.wait();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !closing;
    }

    private void applySeek(long frame) throws IOException {
        output.seek(frame);
        source.seekToFrame(frame);
        running = false;
        ended = false;
        postPosition();
    }

    private void switchRunning(boolean play) {
        if (play) {
            output.start();
        } else {
            output.pause();
        }
        running = play;
    }

    private void pump() throws IOException {
        int count = source.read(buffer);
        if (count == PcmSource.END_OF_STREAM) {
            finishStream();
            return;
        }
        if (count <= 0) return;
        output.write(buffer, count);
        if (Math.abs(output.seconds() - postedSeconds) >= POSITION_STEP_SECONDS) postPosition();
    }

    private void finishStream() {
        output.drain();
        ended = true;
        postPosition();
        listener.ended(generation);
    }

    private void postPosition() {
        postedSeconds = output.seconds();
        listener.positionChanged(postedSeconds, generation);
    }

    private boolean isClosing() {
        synchronized (lock) {
            return closing;
        }
    }

    private void release() {
        try {
            source.close();
        } finally {
            output.close();
        }
    }
}
