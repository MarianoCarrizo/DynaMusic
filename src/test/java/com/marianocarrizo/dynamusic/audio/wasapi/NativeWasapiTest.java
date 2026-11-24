package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("wasapi")
class NativeWasapiTest {

    private static final AudioFormat FORMAT = PcmFormats.signedLittleEndian(24, 96_000f, 2);
    private static final int AWAIT_MILLIS = 2000;
    private static final long HOLD_NANOS = 1_000_000_000L;
    private static final int SILENT_BLOCK_FRAMES = 960;
    private static final int SILENT_BLOCKS = 25;

    @Test
    void probeAnswersForTheDefaultDevice() {
        assertNotNull(new NativeWasapi().probe(FORMAT));
    }

    @Test
    void holdsTheDeviceForOneSecondOfSilence() {
        NativeWasapi api = new NativeWasapi();
        assumeTrue(api.probe(FORMAT) == ExclusiveSupport.SUPPORTED);

        try (ExclusiveStream stream = api.open(FORMAT, WasapiPeriod.DEFAULT_HNS)) {
            stream.submitSilence(stream.periodFrames());
            stream.start();
            long end = System.nanoTime() + HOLD_NANOS;
            while (System.nanoTime() < end) {
                assertTrue(stream.awaitPeriod(AWAIT_MILLIS));
                stream.submitSilence(stream.periodFrames());
            }
            stream.stop();
            stream.reset();
        }
    }

    @Test
    void sinkPlaysHalfASecondOfSilenceAndReportsProgress() {
        NativeWasapi api = new NativeWasapi();
        assumeTrue(api.probe(FORMAT) == ExclusiveSupport.SUPPORTED);
        WasapiExclusiveSink sink = new WasapiExclusiveSink(api, FORMAT);
        byte[] silence = new byte[FORMAT.getFrameSize() * SILENT_BLOCK_FRAMES];

        try {
            sink.start();
            for (int i = 0; i < SILENT_BLOCKS; i++) {
                sink.write(silence, 0, silence.length);
            }
            sink.drain();
            sink.stop();
            assertTrue(sink.framePosition() > 0);
            sink.suspend();
        } finally {
            sink.close();
        }
    }
}
