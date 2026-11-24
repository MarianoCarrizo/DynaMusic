package com.marianocarrizo.dynamusic.service.playback;

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
}
