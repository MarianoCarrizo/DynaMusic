package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.marianocarrizo.dynamusic.audio.stream.FakePcmSink;
import com.marianocarrizo.dynamusic.audio.stream.FakePcmSource;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import com.marianocarrizo.dynamusic.audio.stream.PcmStream;
import java.util.concurrent.Executor;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class StreamingPlanTest {

    private static final AudioFormat FORMAT = PcmFormats.signedLittleEndian(16, 44100, 2);

    private final FakePcmSource source = new FakePcmSource(FORMAT, new byte[400]);
    private final FakePcmSink sink = new FakePcmSink(FORMAT);
    private final PcmStream stream = new PcmStream(source, sink);

    @Test
    void openingBuildsAReadyPlaybackOverTheStream() {
        Playback playback = new StreamingPlan(stream, Runnable::run).open();

        assertEquals(PlaybackStatus.READY, playback.getStatus());
        playback.dispose();
    }

    @Test
    void aFailingConstructionClosesTheSourceAndTheSink() {
        Executor failing = task -> {
            throw new IllegalStateException("no toolkit");
        };

        assertThrows(IllegalStateException.class, () -> new StreamingPlan(stream, failing).open());

        assertEquals(1, source.closes());
        assertEquals(1, sink.closes());
    }

    @Test
    void discardClosesTheSourceAndTheSink() {
        new StreamingPlan(stream, Runnable::run).discard();

        assertEquals(1, source.closes());
        assertEquals(1, sink.closes());
    }
}
