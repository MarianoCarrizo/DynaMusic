package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public final class LinePcmSink implements PcmSink {

    private static final double BUFFER_SECONDS = 0.100;

    private final SourceDataLine line;
    private final AudioFormat format;

    private LinePcmSink(SourceDataLine line, AudioFormat format) {
        this.line = line;
        this.format = format;
    }

    public static boolean supports(AudioFormat format) {
        return AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, format));
    }

    public static LinePcmSink open(AudioFormat format) throws LineUnavailableException {
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, bufferBytes(format));
        return new LinePcmSink(line, format);
    }

    static int bufferBytes(AudioFormat format) {
        int frames = (int) Math.round(format.getSampleRate() * BUFFER_SECONDS);
        return frames * format.getFrameSize();
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public void start() {
        line.start();
    }

    @Override
    public void stop() {
        line.stop();
    }

    @Override
    public int write(byte[] data, int offset, int length) {
        return line.write(data, offset, length);
    }

    @Override
    public void flush() {
        line.flush();
    }

    @Override
    public void drain() {
        line.drain();
    }

    @Override
    public long framePosition() {
        return line.getLongFramePosition();
    }

    @Override
    public void close() {
        line.close();
    }
}
