package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;

public interface PcmSink extends AutoCloseable {

    AudioFormat format();

    void start();

    void stop();

    int write(byte[] data, int offset, int length);

    void flush();

    void drain();

    long framePosition();

    default void suspend() {
    }

    default OutputStatus status() {
        return OutputStatus.of(OutputRoute.SHARED, format());
    }

    @Override
    void close();
}
