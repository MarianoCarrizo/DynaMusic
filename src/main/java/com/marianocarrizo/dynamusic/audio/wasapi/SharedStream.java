package com.marianocarrizo.dynamusic.audio.wasapi;

public interface SharedStream extends AutoCloseable {

    int bufferFrames();

    int padding();

    boolean awaitSpace(int timeoutMillis);

    void submit(byte[] floatFrames, int frames);

    void start();

    void stop();

    void reset();

    @Override
    void close();
}
