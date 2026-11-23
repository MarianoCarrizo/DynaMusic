package com.marianocarrizo.dynamusic.audio.pcm;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class AudioFileGain {

    private static final int BUFFER_BYTES = 16 * 1024;

    private AudioFileGain() {}

    public static double measure(File audioFile) throws IOException {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat format = stream.getFormat();
            boolean signed = format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED;
            boolean unsigned = format.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED;
            if (!signed && !unsigned) throw new IOException("not integer PCM: " + format.getEncoding());
            GainMeter meter = new GainMeter(format.getSampleSizeInBits(), signed, format.isBigEndian());
            byte[] buffer = new byte[BUFFER_BYTES];
            int read = stream.read(buffer);
            while (read != -1) {
                meter.accept(buffer, read);
                read = stream.read(buffer);
            }
            return meter.gain();
        } catch (UnsupportedAudioFileException | IllegalArgumentException unsupported) {
            throw new IOException(unsupported.getMessage(), unsupported);
        }
    }
}
