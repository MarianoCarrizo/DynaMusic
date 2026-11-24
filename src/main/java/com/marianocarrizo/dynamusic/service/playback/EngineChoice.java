package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.audio.PcmProbe;
import com.marianocarrizo.dynamusic.audio.stream.SampleWidth;
import java.io.File;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public enum EngineChoice {
    JAVAFX,
    STREAMING;

    private static final Set<AudioFormat> STREAMABLE_FORMATS = EnumSet.of(AudioFormat.FLAC, AudioFormat.WAV, AudioFormat.AIFF);

    public static boolean couldStream(boolean enabled, File file) {
        return enabled && AudioFormat.fromFile(file).filter(STREAMABLE_FORMATS::contains).isPresent();
    }

    public static EngineChoice of(boolean enabled, PlaybackUse use, File file, Supplier<Optional<PcmProbe.Info>> probe) {
        if (!use.streamable() || !couldStream(enabled, file)) return JAVAFX;
        return probe.get().filter(EngineChoice::hasStreamableSamples).isPresent() ? STREAMING : JAVAFX;
    }

    private static boolean hasStreamableSamples(PcmProbe.Info info) {
        return !info.floatingPoint() && SampleWidth.isSupported(info.bitsPerSample());
    }
}
