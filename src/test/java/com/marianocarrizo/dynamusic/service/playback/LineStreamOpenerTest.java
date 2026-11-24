package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.concurrent.atomic.AtomicInteger;
import com.marianocarrizo.dynamusic.audio.wasapi.WasapiApi;
import com.marianocarrizo.dynamusic.audio.wasapi.ExclusiveSupport;
import com.marianocarrizo.dynamusic.audio.wasapi.ExclusiveStream;
import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.FakePcmSink;
import com.marianocarrizo.dynamusic.audio.stream.FakePcmSource;
import com.marianocarrizo.dynamusic.audio.stream.OutputNegotiator;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import org.junit.jupiter.api.Test;

class LineStreamOpenerTest {

    private static final File FILE = new File("track.flac");
    private static final AudioFormat CD = PcmFormats.signedLittleEndian(16, 44100, 2);
    private static final AudioFormat HI_RES = PcmFormats.signedLittleEndian(24, 96000, 2);
    private static final int FRAMES = 1000;

    private final List<AudioFormat> sinkRequests = new ArrayList<>();
    private final List<AudioFormat> resampleTargets = new ArrayList<>();
    private final List<Long> resampleFrames = new ArrayList<>();
    private FakePcmSource source = sourceOf(CD);
    private FakePcmSource resampled;
    private FakePcmSink sink;
    private FakePcmSink exclusiveSink;
    private final List<AudioFormat> exclusiveRequests = new ArrayList<>();
    private boolean sinkFails;
    private boolean sourceFails;

    private static FakePcmSource sourceOf(AudioFormat format) {
        return new FakePcmSource(format, new byte[FRAMES * format.getFrameSize()]);
    }

    private LineStreamOpener opener(Predicate<AudioFormat> lineSupports, boolean ffmpeg) {
        return new LineStreamOpener(file -> {
            if (sourceFails) throw new IOException("corrupt header");
            return source;
        }, format -> {
            sinkRequests.add(format);
            if (sinkFails) throw new LineUnavailableException("busy");
            sink = new FakePcmSink(format);
            return sink;
        }, (file, target, frames) -> {
            resampleTargets.add(target);
            resampleFrames.add(frames);
            resampled = sourceOf(target);
            return resampled;
        }, new OutputNegotiator(lineSupports, () -> ffmpeg));
    }

    @Test
    void exactFormatOpensTheSourceAndASinkInTheSameFormat() {
        Optional<PcmStream> stream = opener(format -> true, false).open(FILE);

        assertSame(source, stream.orElseThrow().source());
        assertSame(sink, stream.orElseThrow().sink());
        assertEquals(List.of(CD), sinkRequests);
        assertEquals(0, source.closes());
    }

    @Test
    void reducedDepthOpensTheSinkAtSixteenBits() {
        source = sourceOf(HI_RES);

        Optional<PcmStream> stream = opener(format -> format.getSampleSizeInBits() == 16, false).open(FILE);

        assertSame(source, stream.orElseThrow().source());
        assertEquals(16, sinkRequests.get(0).getSampleSizeInBits());
        assertEquals(96000f, sinkRequests.get(0).getSampleRate());
    }

    @Test
    void resampledPlansReplaceTheSourceAndConvertTheFrameCount() {
        source = sourceOf(HI_RES);

        Optional<PcmStream> stream = opener(format -> format.getSampleRate() <= 48000, true).open(FILE);

        assertSame(resampled, stream.orElseThrow().source());
        assertEquals(1, source.closes());
        assertEquals(48000f, resampleTargets.get(0).getSampleRate());
        assertEquals(List.of((long) FRAMES / 2), resampleFrames);
        assertEquals(resampleTargets, sinkRequests);
    }

    @Test
    void anUnopenableSourceYieldsNothing() {
        sourceFails = true;

        assertTrue(opener(format -> true, true).open(FILE).isEmpty());
        assertTrue(sinkRequests.isEmpty());
    }

    @Test
    void moreThanTwoChannelsYieldsNothingAndClosesTheSource() {
        source = sourceOf(PcmFormats.signedLittleEndian(16, 44100, 6));

        assertTrue(opener(format -> true, true).open(FILE).isEmpty());
        assertEquals(1, source.closes());
        assertTrue(sinkRequests.isEmpty());
    }

    @Test
    void aNegotiationThatNeedsJavaFxYieldsNothingAndClosesTheSource() {
        source = sourceOf(HI_RES);

        assertTrue(opener(format -> false, false).open(FILE).isEmpty());
        assertEquals(1, source.closes());
        assertTrue(sinkRequests.isEmpty());
    }

    @Test
    void anUnavailableLineClosesTheSource() {
        sinkFails = true;

        assertTrue(opener(format -> true, false).open(FILE).isEmpty());
        assertEquals(1, source.closes());
    }

    @Test
    void anUnavailableLineAfterResamplingClosesTheResampledSource() {
        source = sourceOf(HI_RES);
        sinkFails = true;

        assertTrue(opener(format -> format.getSampleRate() <= 48000, true).open(FILE).isEmpty());
        assertEquals(1, resampled.closes());
    }

    private LineStreamOpener exclusiveOpener(Predicate<AudioFormat> exclusive) {
        return new LineStreamOpener(file -> source, format -> {
            sinkRequests.add(format);
            sink = new FakePcmSink(format);
            return sink;
        }, format -> {
            exclusiveRequests.add(format);
            exclusiveSink = new FakePcmSink(format);
            return exclusiveSink;
        }, (file, target, frames) -> sourceOf(target),
                new OutputNegotiator(exclusive, format -> true, () -> false));
    }

    private static WasapiApi probing(ExclusiveSupport support, AtomicInteger probes) {
        return new WasapiApi() {
            @Override
            public ExclusiveSupport probe(AudioFormat format) {
                probes.incrementAndGet();
                if (support == null) throw new IllegalStateException("no native layer");
                return support;
            }

            @Override
            public ExclusiveStream open(AudioFormat format, long periodHns) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void anExclusivePlanOpensTheExclusiveSinkInTheSourceFormat() {
        source = sourceOf(HI_RES);

        Optional<PcmStream> stream = exclusiveOpener(format -> true).open(FILE);

        assertSame(exclusiveSink, stream.orElseThrow().sink());
        assertEquals(List.of(HI_RES), exclusiveRequests);
        assertTrue(sinkRequests.isEmpty());
    }

    @Test
    void aRejectedExclusiveProbeOpensTheSharedSink() {
        source = sourceOf(HI_RES);

        Optional<PcmStream> stream = exclusiveOpener(format -> false).open(FILE);

        assertSame(sink, stream.orElseThrow().sink());
        assertTrue(exclusiveRequests.isEmpty());
    }

    @Test
    void theSettingOffNeverProbesTheDevice() {
        AtomicInteger probes = new AtomicInteger();
        Predicate<AudioFormat> offer = LineStreamOpener.exclusiveOffer(() -> false, new OutputArbiter(),
                probing(ExclusiveSupport.SUPPORTED, probes));

        assertFalse(offer.test(HI_RES));
        assertEquals(0, probes.get());
    }

    @Test
    void sharedPlaybackInProgressSkipsTheExclusiveOffer() {
        AtomicInteger probes = new AtomicInteger();
        OutputArbiter arbiter = new OutputArbiter();
        arbiter.sharedStarting(new Object(), 1);
        Predicate<AudioFormat> offer = LineStreamOpener.exclusiveOffer(() -> true, arbiter,
                probing(ExclusiveSupport.SUPPORTED, probes));

        assertFalse(offer.test(HI_RES));
        assertEquals(0, probes.get());
    }

    @Test
    void onlyASupportedProbeOffersExclusive() {
        for (ExclusiveSupport support : ExclusiveSupport.values()) {
            Predicate<AudioFormat> offer = LineStreamOpener.exclusiveOffer(() -> true, new OutputArbiter(),
                    probing(support, new AtomicInteger()));

            assertEquals(support == ExclusiveSupport.SUPPORTED, offer.test(HI_RES), support.name());
        }
    }

    @Test
    void aFailingNativeLayerDeclinesExclusiveInsteadOfThrowing() {
        Predicate<AudioFormat> offer = LineStreamOpener.exclusiveOffer(() -> true, new OutputArbiter(),
                probing(null, new AtomicInteger()));

        assertFalse(offer.test(HI_RES));
    }
}
