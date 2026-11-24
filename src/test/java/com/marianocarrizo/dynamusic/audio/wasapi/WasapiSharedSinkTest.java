package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.OutputRoute;
import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class WasapiSharedSinkTest {

    private static final int BUFFER = 8;
    private static final int PER_EVENT = 4;
    private static final AudioFormat TWENTY_FOUR_BIT = PcmFormats.signedLittleEndian(24, 96_000f, 2);
    private static final AudioFormat SIXTEEN_BIT_MONO = PcmFormats.signedLittleEndian(16, 44_100f, 1);

    private static FakeSharedApi api() {
        return new FakeSharedApi(BUFFER, PER_EVENT);
    }

    private static WasapiSharedSink started(FakeSharedApi api, AudioFormat format) {
        WasapiSharedSink sink = new WasapiSharedSink(api, format);
        sink.start();
        return sink;
    }

    private static void write(WasapiSharedSink sink, byte[] data) {
        assertEquals(data.length, sink.write(data, 0, data.length));
    }

    private static float[] floats(byte[] bytes) {
        float[] values = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return values;
    }

    private static byte[] frames24(int frameCount, int firstValue) {
        return PcmFrames.pack(3, PcmFrames.ramp(frameCount * 2, firstValue));
    }

    @Test
    void theSinkReportsTheSourceFormatAndTheWasapiSharedRoute() {
        WasapiSharedSink sink = new WasapiSharedSink(api(), TWENTY_FOUR_BIT);

        assertEquals(TWENTY_FOUR_BIT, sink.format());
        assertEquals(OutputRoute.WASAPI_SHARED, sink.status().route());
        assertEquals("24/96 shared (WASAPI)", sink.status().label());
    }

    @Test
    void nothingIsOpenedUntilTheSinkStarts() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = new WasapiSharedSink(api, TWENTY_FOUR_BIT);

        sink.stop();
        sink.flush();
        sink.drain();
        sink.suspend();

        assertEquals(0, api.opens());
        assertEquals(0, sink.framePosition());
    }

    @Test
    void startOpensTheStreamOnceAndStartsIt() {
        FakeSharedApi api = api();

        started(api, TWENTY_FOUR_BIT).start();

        assertEquals(1, api.opens());
        assertEquals(List.of("start"), api.stream().calls());
    }

    @Test
    void twentyFourBitSamplesReachTheDeviceAsExactFloats() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);

        write(sink, frames24(2, 8_388_600));

        float[] sent = floats(api.stream().submittedBytes());
        assertEquals(4, sent.length);
        for (int i = 0; i < sent.length; i++) {
            assertEquals((8_388_600 + i) / 8_388_608f, sent[i]);
        }
    }

    @Test
    void monoSourcesAreDuplicatedToTwoChannels() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, SIXTEEN_BIT_MONO);

        write(sink, PcmFrames.pack(2, 16384, -16384));

        float[] sent = floats(api.stream().submittedBytes());
        assertEquals(4, sent.length);
        assertEquals(0.5f, sent[1]);
        assertEquals(-0.5f, sent[2]);
    }

    @Test
    void writesLargerThanTheBufferWaitForSpaceAndLoseNothing() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);

        write(sink, frames24(30, 1));

        assertEquals(30, api.stream().submittedFrames());
        float[] sent = floats(api.stream().submittedBytes());
        for (int i = 0; i < sent.length; i++) {
            assertEquals((1 + i) / 8_388_608f, sent[i]);
        }
    }

    @Test
    void partialSpaceIsFilledWithoutWaitingForAWholeBuffer() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(BUFFER, 1));

        write(sink, frames24(2, 100));

        assertEquals(BUFFER + 2, api.stream().submittedFrames());
    }

    @Test
    void positionFollowsTheFramesTheDeviceHasConsumed() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);

        write(sink, frames24(BUFFER, 1));
        assertEquals(0, sink.framePosition());

        write(sink, frames24(PER_EVENT, 1));
        assertEquals(PER_EVENT, sink.framePosition());
    }

    @Test
    void stopStopsTheStreamAndKeepsTheDeviceOpen() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);

        sink.stop();

        assertEquals(List.of("start", "stop"), api.stream().calls());
        assertEquals(0, api.stream().closes());
    }

    @Test
    void flushDiscardsQueuedFramesWithoutMovingThePositionBackwards() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(BUFFER + PER_EVENT, 1));
        long before = sink.framePosition();

        sink.flush();

        assertTrue(api.stream().calls().contains("reset"));
        assertEquals(before, sink.framePosition());
        assertEquals(0, api.stream().padding());
    }

    @Test
    void aFlushedSinkResumesFromTheSamePositionAfterRestart() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(BUFFER + PER_EVENT, 1));
        sink.flush();
        long flushed = sink.framePosition();

        sink.start();
        write(sink, frames24(BUFFER, 1));
        write(sink, frames24(PER_EVENT, 1));

        assertEquals(flushed + PER_EVENT, sink.framePosition());
        assertEquals(1, api.opens());
    }

    @Test
    void drainWaitsUntilTheDeviceHasPlayedEverything() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(BUFFER, 1));

        sink.drain();

        assertEquals(0, api.stream().padding());
        assertEquals(BUFFER, sink.framePosition());
    }

    @Test
    void drainSamplesThePositionWhenTheDeviceAlreadyPlayedEverything() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(PER_EVENT, 1));
        api.stream().consumeEverything();

        sink.drain();

        assertEquals(PER_EVENT, sink.framePosition());
    }

    @Test
    void drainGivesUpWhenTheDeviceStopsSignalling() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(BUFFER, 1));
        api.stream().stopSignalling();

        sink.drain();

        assertEquals(BUFFER, api.stream().padding());
    }

    @Test
    void aSilentDeviceRaisesUnavailableOnWrite() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(BUFFER, 1));
        api.stream().stopSignalling();
        byte[] data = frames24(2, 1);

        assertThrows(OutputUnavailableException.class, () -> sink.write(data, 0, data.length));
    }

    @Test
    void aFailedSubmitReportsHowManyBytesWereAlreadyAccepted() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        api.stream().failSubmitsAfter(1, new OutputUnavailableException("device lost"));
        byte[] data = frames24(12, 1);

        OutputUnavailableException failure = assertThrows(OutputUnavailableException.class,
                () -> sink.write(data, 0, data.length));

        assertEquals(BUFFER * TWENTY_FOUR_BIT.getFrameSize(), failure.consumedBytes());
    }

    @Test
    void aFailedOpenLeavesTheSinkStoppedAndRaisesUnavailable() {
        FakeSharedApi api = api();
        api.failOpensWith(new OutputUnavailableException("no device"));
        WasapiSharedSink sink = new WasapiSharedSink(api, TWENTY_FOUR_BIT);

        assertThrows(OutputUnavailableException.class, sink::start);
        assertThrows(IllegalStateException.class, () -> sink.write(new byte[6], 0, 6));
    }

    @Test
    void suspendKeepsTheStreamOpenBecauseSharedModeDoesNotNeedTheDevice() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);
        sink.flush();

        sink.suspend();

        assertEquals(0, api.stream().closes());
    }

    @Test
    void closeReleasesTheStreamOnce() {
        FakeSharedApi api = api();
        WasapiSharedSink sink = started(api, TWENTY_FOUR_BIT);

        sink.close();
        sink.close();

        assertEquals(1, api.stream().closes());
        assertThrows(IllegalStateException.class, sink::start);
    }
}
