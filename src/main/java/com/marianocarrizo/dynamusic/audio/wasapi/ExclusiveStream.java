package com.marianocarrizo.dynamusic.audio.wasapi;

public interface ExclusiveStream extends AutoCloseable {

    SampleContainer container();

    int periodFrames();

    int padding();

    boolean awaitPeriod(int timeoutMillis);

    void submit(byte[] containerBytes, int frames);

    void submitSilence(int frames);

    void start();

    void stop();

    void reset();

    @Override
    void close();
}
