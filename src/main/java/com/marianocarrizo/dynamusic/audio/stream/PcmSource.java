package com.marianocarrizo.dynamusic.audio.stream;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;

public interface PcmSource extends AutoCloseable {

    int END_OF_STREAM = -1;

    AudioFormat format();

    long totalFrames();

    int read(byte[] buffer) throws IOException;

    void seekToFrame(long frame) throws IOException;

    @Override
    void close();
}
