package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.audio.stream.FfmpegResampleSource;
import com.marianocarrizo.dynamusic.audio.stream.FlacPcmSource;
import com.marianocarrizo.dynamusic.audio.stream.JavaSoundPcmSource;
import com.marianocarrizo.dynamusic.audio.stream.PcmSource;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;
import java.io.File;
import java.io.IOException;

final class StreamSources {

    private StreamSources() {
    }

    static PcmSource open(File file) throws IOException {
        return AudioFormat.FLAC.matches(file) ? FlacPcmSource.open(file) : JavaSoundPcmSource.open(file);
    }

    static PcmSource resample(File file, javax.sound.sampled.AudioFormat target, long totalFrames) throws IOException {
        String executable = FfmpegLocator.executable().orElseThrow(() -> new IOException("ffmpeg was not found"));
        return FfmpegResampleSource.open(executable, file, target, totalFrames);
    }
}
