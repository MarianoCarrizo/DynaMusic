package com.marianocarrizo.dynamusic.audio.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;

final class FakePcmSource implements PcmSource {

    private final AudioFormat format;
    private final byte[] data;
    private int position;
    private int closes;
    private int reads;
    private int failOnRead = -1;
    private final List<Long> seeks = new ArrayList<>();

    FakePcmSource(AudioFormat format, byte[] data) {
        this.format = format;
        this.data = data;
    }

    void failOnRead(int readNumber) {
        failOnRead = readNumber;
    }

    List<Long> seeks() {
        return seeks;
    }

    int closes() {
        return closes;
    }

    long positionFrame() {
        return position / format.getFrameSize();
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public long totalFrames() {
        return data.length / format.getFrameSize();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        reads++;
        if (reads == failOnRead) throw new IOException("read failed");
        if (position >= data.length) return END_OF_STREAM;
        int frameBytes = format.getFrameSize();
        int count = Math.min(buffer.length - buffer.length % frameBytes, data.length - position);
        System.arraycopy(data, position, buffer, 0, count);
        position += count;
        return count;
    }

    @Override
    public void seekToFrame(long frame) {
        seeks.add(frame);
        position = (int) (frame * format.getFrameSize());
    }

    @Override
    public void close() {
        closes++;
    }
}
