package com.marianocarrizo.dynamusic.audio.pcm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class JavaSoundConverter implements PcmConverter {

    static final int MAX_OUTPUT_BITS = 24;
    private static final int DEFAULT_BITS = 16;
    private static final int BITS_PER_BYTE = 8;

    @Override
    public void convert(File source, Path targetWav) throws IOException {
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(source);
             AudioInputStream pcm = AudioSystem.getAudioInputStream(pcmFormatFor(encoded.getFormat()), encoded)) {
            AudioSystem.write(pcm, AudioFileFormat.Type.WAVE, targetWav.toFile());
        } catch (UnsupportedAudioFileException | RuntimeException | IOException failure) {
            Files.deleteIfExists(targetWav);
            throw failure instanceof IOException io ? io : new IOException(failure.getMessage(), failure);
        }
    }

    static AudioFormat pcmFormatFor(AudioFormat encoded) {
        int sourceBits = encoded.getSampleSizeInBits();
        int bits = sourceBits <= 0 ? DEFAULT_BITS : Math.min(sourceBits, MAX_OUTPUT_BITS);
        int channels = encoded.getChannels();
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, encoded.getSampleRate(), bits, channels,
                channels * bits / BITS_PER_BYTE, encoded.getSampleRate(), false);
    }
}
