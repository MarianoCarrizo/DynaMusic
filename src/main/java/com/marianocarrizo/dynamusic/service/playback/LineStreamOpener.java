package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.ExclusiveFallbackSink;
import com.marianocarrizo.dynamusic.audio.stream.JavaSoundFallbackSink;
import com.marianocarrizo.dynamusic.audio.stream.LinePcmSink;
import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import com.marianocarrizo.dynamusic.audio.stream.OutputChoice;
import com.marianocarrizo.dynamusic.audio.stream.OutputNegotiator;
import com.marianocarrizo.dynamusic.audio.stream.OutputPlan;
import com.marianocarrizo.dynamusic.audio.stream.PcmSink;
import com.marianocarrizo.dynamusic.audio.stream.PcmSource;
import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import com.marianocarrizo.dynamusic.audio.stream.SharedLines;
import com.marianocarrizo.dynamusic.audio.wasapi.ExclusiveSupport;
import com.marianocarrizo.dynamusic.audio.wasapi.NativeWasapi;
import com.marianocarrizo.dynamusic.audio.wasapi.WasapiApi;
import com.marianocarrizo.dynamusic.audio.wasapi.WasapiExclusiveSink;
import com.marianocarrizo.dynamusic.audio.wasapi.WasapiSharedApi;
import com.marianocarrizo.dynamusic.audio.wasapi.WasapiSharedSink;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
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
    private final SinkFactory exclusiveSinks;
    private final SinkFactory wasapiSharedSinks;
    private final ResampleFactory resamplers;
    private final OutputNegotiator negotiator;

    LineStreamOpener(SourceFactory sources, SinkFactory sinks, ResampleFactory resamplers,
            OutputNegotiator negotiator) {
        this(sources, sinks, sinks, resamplers, negotiator);
    }

    LineStreamOpener(SourceFactory sources, SinkFactory sinks, SinkFactory exclusiveSinks,
            ResampleFactory resamplers, OutputNegotiator negotiator) {
        this(sources, sinks, exclusiveSinks, sinks, resamplers, negotiator);
    }

    LineStreamOpener(SourceFactory sources, SinkFactory sinks, SinkFactory exclusiveSinks,
            SinkFactory wasapiSharedSinks, ResampleFactory resamplers, OutputNegotiator negotiator) {
        this.sources = sources;
        this.sinks = sinks;
        this.exclusiveSinks = exclusiveSinks;
        this.wasapiSharedSinks = wasapiSharedSinks;
        this.resamplers = resamplers;
        this.negotiator = negotiator;
    }

    public static LineStreamOpener standard(BooleanSupplier exclusiveEnabled, OutputArbiter arbiter) {
        NativeWasapi wasapi = new NativeWasapi();
        SinkFactory exclusive = format -> new ExclusiveFallbackSink(new WasapiExclusiveSink(wasapi, format),
                SharedLines.system(), arbiter);
        SinkFactory wasapiShared = format -> new JavaSoundFallbackSink(new WasapiSharedSink(wasapi, format),
                SharedLines.system());
        return new LineStreamOpener(StreamSources::open, LinePcmSink::open, exclusive, wasapiShared,
                StreamSources::resample,
                new OutputNegotiator(exclusiveOffer(exclusiveEnabled, arbiter, wasapi), sharedOffer(wasapi),
                        LinePcmSink::supports, FfmpegLocator::awaitAvailability));
    }

    static Predicate<AudioFormat> sharedOffer(WasapiSharedApi wasapi) {
        return format -> {
            try {
                return wasapi.supportsShared(format);
            } catch (RuntimeException | LinkageError unavailable) {
                return false;
            }
        };
    }

    static Predicate<AudioFormat> exclusiveOffer(BooleanSupplier enabled, OutputArbiter arbiter, WasapiApi wasapi) {
        return format -> {
            if (!enabled.getAsBoolean() || arbiter.sharedInUse()) return false;
            try {
                return wasapi.probe(format) == ExclusiveSupport.SUPPORTED;
            } catch (RuntimeException | LinkageError unavailable) {
                return false;
            }
        };
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
            return Optional.of(new PcmStream(playable, sinkFor(choice.get())));
        } catch (IOException | LineUnavailableException | RuntimeException | LinkageError failure) {
            closeQuietly(source);
            return Optional.empty();
        }
    }

    private PcmSink sinkFor(OutputChoice choice) throws LineUnavailableException {
        SinkFactory factory = switch (choice.plan()) {
            case EXCLUSIVE -> exclusiveSinks;
            case WASAPI_SHARED -> wasapiSharedSinks;
            default -> sinks;
        };
        return factory.open(choice.sinkFormat());
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
