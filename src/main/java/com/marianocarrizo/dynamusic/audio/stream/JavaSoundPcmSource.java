package com.marianocarrizo.dynamusic.audio.stream;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class JavaSoundPcmSource implements PcmSource {

    private final File file;
    private final AudioFormat format;
    private final int frameBytes;
    private final long totalFrames;
    private AudioInputStream stream;

    private JavaSoundPcmSource(File file, AudioFormat format, long totalFrames) throws IOException {
        this.file = file;
        this.format = format;
        this.frameBytes = format.getFrameSize();
        this.totalFrames = totalFrames;
        this.stream = openAt(0);
    }

    public static JavaSoundPcmSource open(File file) throws IOException {
        try (AudioInputStream probe = AudioSystem.getAudioInputStream(file)) {
            AudioFormat source = probe.getFormat();
            if (source.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                    || !SampleWidth.isSupported(source.getSampleSizeInBits())) {
                throw new IOException("Unsupported PCM layout: " + source);
            }
            AudioFormat target = PcmFormats.signedLittleEndian(source.getSampleSizeInBits(), source.getSampleRate(),
                    source.getChannels());
            long frames = probe.getFrameLength();
            return new JavaSoundPcmSource(file, target, Math.max(frames, 0));
        } catch (UnsupportedAudioFileException unsupported) {
            throw new IOException("Unsupported audio file", unsupported);
        }
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public long totalFrames() {
        return totalFrames;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        if (stream == null) return END_OF_STREAM;
        int aligned = buffer.length - buffer.length % frameBytes;
        int count = stream.read(buffer, 0, aligned);
        return count <= 0 ? END_OF_STREAM : count;
    }

    @Override
    public void seekToFrame(long frame) throws IOException {
        long target = Math.max(0, totalFrames > 0 ? Math.min(frame, totalFrames) : frame);
        closeStream();
        stream = openAt(target);
    }

    @Override
    public void close() {
        closeStream();
    }

    private AudioInputStream openAt(long frame) throws IOException {
        try {
            AudioInputStream raw = AudioSystem.getAudioInputStream(file);
            try {
                skipFully(raw, frame * raw.getFormat().getFrameSize());
                return raw.getFormat().equals(format) ? raw : AudioSystem.getAudioInputStream(format, raw);
            } catch (IOException | RuntimeException failure) {
                raw.close();
                throw failure;
            }
        } catch (UnsupportedAudioFileException unsupported) {
            throw new IOException("Unsupported audio file", unsupported);
        }
    }

    private static void skipFully(AudioInputStream raw, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = raw.skip(remaining);
            if (skipped <= 0) break;
            remaining -= skipped;
        }
    }

    private void closeStream() {
        AudioInputStream current = stream;
        stream = null;
        if (current == null) return;
        try {
            current.close();
        } catch (IOException alreadyClosed) {
            return;
        }
    }
}
