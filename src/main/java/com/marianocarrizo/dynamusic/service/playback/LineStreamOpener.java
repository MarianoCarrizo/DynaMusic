package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.LinePcmSink;
import com.marianocarrizo.dynamusic.audio.stream.OutputChoice;
import com.marianocarrizo.dynamusic.audio.stream.OutputNegotiator;
import com.marianocarrizo.dynamusic.audio.stream.OutputPlan;
import com.marianocarrizo.dynamusic.audio.stream.PcmSink;
import com.marianocarrizo.dynamusic.audio.stream.PcmSource;
import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

public final class LineStreamOpener implements StreamOpener {

    private static final int MAX_CHANNELS = 2;

    @FunctionalInterface
    interface SourceFactory {
        PcmSource open(File file) throws IOException;
    }

    @FunctionalInterface
    interface SinkFactory {
        PcmSink open(AudioFormat format) throws LineUnavailableException;
    }

    @FunctionalInterface
    interface ResampleFactory {
        PcmSource open(File file, AudioFormat target, long totalFrames) throws IOException;
    }

    private final SourceFactory sources;
    private final SinkFactory sinks;
    private final ResampleFactory resamplers;
    private final OutputNegotiator negotiator;

    LineStreamOpener(SourceFactory sources, SinkFactory sinks, ResampleFactory resamplers,
            OutputNegotiator negotiator) {
        this.sources = sources;
        this.sinks = sinks;
        this.resamplers = resamplers;
        this.negotiator = negotiator;
    }

    public static LineStreamOpener standard() {
        return new LineStreamOpener(StreamSources::open, LinePcmSink::open, StreamSources::resample,
                new OutputNegotiator(LinePcmSink::supports, FfmpegLocator::awaitAvailability));
    }

    @Override
    public Optional<PcmStream> open(File file) {
        PcmSource source = null;
        try {
            source = sources.open(file);
            Optional<OutputChoice> choice = choiceFor(source.format());
            if (choice.isEmpty()) {
                source.close();
                return Optional.empty();
            }
            PcmSource playable = playableSource(file, source, choice.get());
            source = playable;
            return Optional.of(new PcmStream(playable, sinks.open(choice.get().sinkFormat())));
        } catch (IOException | LineUnavailableException | RuntimeException failure) {
            closeQuietly(source);
            return Optional.empty();
        }
    }

    private Optional<OutputChoice> choiceFor(AudioFormat sourceFormat) {
        if (sourceFormat.getChannels() > MAX_CHANNELS) return Optional.empty();
        OutputChoice choice = negotiator.negotiate(sourceFormat);
        return choice.plan().streams() ? Optional.of(choice) : Optional.empty();
    }

    private PcmSource playableSource(File file, PcmSource source, OutputChoice choice) throws IOException {
        if (choice.plan() != OutputPlan.RESAMPLED) return source;
        double ratio = choice.sinkFormat().getSampleRate() / source.format().getSampleRate();
        PcmSource resampled = resamplers.open(file, choice.sinkFormat(), Math.round(source.totalFrames() * ratio));
        source.close();
        return resampled;
    }

    private static void closeQuietly(PcmSource source) {
        if (source == null) return;
        try {
            source.close();
        } catch (RuntimeException alreadyClosed) {
            return;
        }
    }
}
