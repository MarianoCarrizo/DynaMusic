package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import com.marianocarrizo.dynamusic.audio.stream.PcmFormats;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class WasapiExclusiveSinkTest {

    private static final int PERIOD = 4;
    private static final AudioFormat TWENTY_FOUR_BIT = PcmFormats.signedLittleEndian(24, 96_000f, 2);
    private static final AudioFormat SIXTEEN_BIT = PcmFormats.signedLittleEndian(16, 44_100f, 2);

    private static byte[] frames24(int frameCount, int firstValue) {
        return PcmFrames.pack(3, PcmFrames.ramp(frameCount * 2, firstValue));
    }

    private static WasapiExclusiveSink started(FakeWasapiApi api, AudioFormat format) {
        WasapiExclusiveSink sink = new WasapiExclusiveSink(api, format);
        sink.start();
        return sink;
    }

    private static void write(WasapiExclusiveSink sink, byte[] data) {
        assertEquals(data.length, sink.write(data, 0, data.length));
    }

    private static FakeWasapiApi apiWith(SampleContainer container) {
        return new FakeWasapiApi(container, PERIOD);
    }

    @Test
    void theSinkReportsTheSourceFormat() {
        WasapiExclusiveSink sink = new WasapiExclusiveSink(apiWith(SampleContainer.PADDED_32), TWENTY_FOUR_BIT);

        assertEquals(TWENTY_FOUR_BIT, sink.format());
    }

    @Test
    void nothingIsOpenedUntilTheSinkStarts() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = new WasapiExclusiveSink(api, TWENTY_FOUR_BIT);

        sink.stop();
        sink.flush();
        sink.drain();
        sink.suspend();

        assertEquals(0, api.opens());
        assertEquals(0, sink.framePosition());
    }

    @Test
    void startPrerollsOneSilentPeriodBeforeStartingTheDevice() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);

        started(api, TWENTY_FOUR_BIT).start();

        assertEquals(List.of("silence", "start"), api.stream().calls());
    }

    @Test
    void onlyWholePeriodsReachTheDevice() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);

        write(sink, frames24(6, 1));
        assertEquals(1, api.stream().calls().stream().filter("submit"::equals).count());

        write(sink, frames24(2, 100));
        assertEquals(2, api.stream().calls().stream().filter("submit"::equals).count());
    }

    @Test
    void writeIgnoresATrailingPartialFrame() {
        WasapiExclusiveSink sink = started(apiWith(SampleContainer.PADDED_32), TWENTY_FOUR_BIT);
        byte[] data = new byte[frames24(PERIOD, 1).length + 1];

        assertEquals(data.length - 1, sink.write(data, 0, data.length));
    }

    @Test
    void writingBeforeStartingIsRejected() {
        WasapiExclusiveSink sink = new WasapiExclusiveSink(apiWith(SampleContainer.PADDED_32), TWENTY_FOUR_BIT);

        assertThrows(IllegalStateException.class, () -> sink.write(new byte[6], 0, 6));
    }

    @Test
    void twentyFourBitSourceReachesThePaddedDeviceBufferUnchanged() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        int[] source = {1, -1, 0x7FFFFF, -0x800000, 0, 2, 0x123456, -0x123456};

        write(sink, PcmFrames.pack(3, source));

        byte[] device = api.stream().submittedBytes();
        int[] deviceSamples = PcmFrames.unpack(4, device);
        assertEquals(source.length, deviceSamples.length);
        for (int i = 0; i < source.length; i++) {
            assertEquals(source[i], deviceSamples[i] >> 8);
            assertEquals(0, deviceSamples[i] & 0xFF);
        }
    }

    @Test
    void packedTwentyFourContainerReceivesTheSourceBytesAsTheyAre() {
        FakeWasapiApi api = apiWith(SampleContainer.PACKED_24);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        byte[] source = frames24(PERIOD, 10);

        write(sink, source);

        assertArrayEquals(source, api.stream().submittedBytes());
    }

    @Test
    void sixteenBitSourceIsLeftJustifiedIntoAThirtyTwoBitContainer() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, SIXTEEN_BIT);
        int[] source = {1, -1, 0x7FFF, -0x8000, 0, 5, -5, 300};

        write(sink, PcmFrames.pack(2, source));

        int[] device = PcmFrames.unpack(4, api.stream().submittedBytes());
        for (int i = 0; i < source.length; i++) {
            assertEquals(source[i] << 16, device[i]);
        }
    }

    @Test
    void sixteenBitSourceUsesNativeSixteenBitWhenTheDeviceGivesIt() {
        FakeWasapiApi api = apiWith(SampleContainer.PACKED_16);
        WasapiExclusiveSink sink = started(api, SIXTEEN_BIT);
        byte[] source = PcmFrames.pack(2, PcmFrames.ramp(PERIOD * 2, -3));

        write(sink, source);

        assertArrayEquals(source, api.stream().submittedBytes());
    }

    @Test
    void positionLagsTheWritesByThePrerollAndThePeriodInFlight() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);

        write(sink, frames24(3 * PERIOD, 1));

        assertEquals(PERIOD, sink.framePosition());
    }

    @Test
    void positionEqualsTheFramesPlayedOnceTheDeviceHasPlayedEverything() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(3 * PERIOD, 1));

        api.stream().playEverything();
        sink.stop();

        assertEquals(3 * PERIOD, sink.framePosition());
    }

    @Test
    void positionStaysFlatWhilePausedAndAfterFlush() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(2 * PERIOD, 1));
        api.stream().playFrames(6);
        sink.stop();
        long paused = sink.framePosition();

        sink.flush();
        sink.stop();

        assertEquals(6, paused);
        assertEquals(paused, sink.framePosition());
    }

    @Test
    void positionContinuesFromWhereTheFlushLeftIt() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(2 * PERIOD, 1));
        api.stream().playFrames(6);
        sink.stop();
        sink.flush();
        long beforeRestart = sink.framePosition();

        sink.start();
        write(sink, frames24(2 * PERIOD, 50));
        api.stream().playEverything();
        sink.stop();

        assertEquals(6, beforeRestart);
        assertEquals(beforeRestart + 2 * PERIOD, sink.framePosition());
    }

    @Test
    void positionNeverMovesBackwards() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        long last = 0;

        for (int i = 0; i < 10; i++) {
            write(sink, frames24(PERIOD, i));
            assertTrue(sink.framePosition() >= last);
            last = sink.framePosition();
        }
    }

    @Test
    void stopAndFlushResetTheDeviceWhileTheStreamStaysOpen() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);

        sink.stop();
        sink.flush();

        assertEquals(List.of("silence", "start", "stop", "reset"), api.stream().calls());
        assertEquals(0, api.stream().closes());
    }

    @Test
    void flushDropsTheAudioStillWaitingForAFullPeriod() {
        FakeWasapiApi api = apiWith(SampleContainer.PACKED_24);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(2, 900));

        sink.flush();
        sink.start();
        byte[] fresh = frames24(PERIOD, 1);
        write(sink, fresh);

        assertArrayEquals(fresh, api.stream().submittedBytes());
    }

    @Test
    void drainPadsTheLastPeriodWithSilenceThenSubmitsTwoSilentPeriods() {
        FakeWasapiApi api = apiWith(SampleContainer.PACKED_24);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        byte[] tail = frames24(2, 7);
        write(sink, tail);

        sink.drain();

        byte[] expected = new byte[PERIOD * 6];
        System.arraycopy(tail, 0, expected, 0, tail.length);
        assertArrayEquals(expected, api.stream().submittedBytes());
        assertEquals(List.of("silence", "start", "submit", "silence", "silence"), api.stream().calls());
    }

    @Test
    void positionEqualsTheFramesWrittenAfterDrain() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(6, 1));

        sink.drain();

        assertEquals(6, sink.framePosition());
    }

    @Test
    void suspendReleasesTheDeviceAndStartReacquiresIt() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(2 * PERIOD, 1));
        api.stream().playEverything();
        sink.stop();
        FakeExclusiveStream first = api.stream();

        sink.suspend();

        assertEquals(1, first.closes());
        assertEquals(1, api.opens());
        long suspendedAt = sink.framePosition();

        sink.start();

        assertEquals(2, api.opens());
        assertEquals(List.of("silence", "start"), api.stream().calls());
        assertEquals(suspendedAt, sink.framePosition());
    }

    @Test
    void positionKeepsCountingAcrossAReacquire() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        write(sink, frames24(2 * PERIOD, 1));
        api.stream().playEverything();
        sink.stop();
        sink.suspend();
        long suspendedAt = sink.framePosition();

        sink.start();
        write(sink, frames24(PERIOD, 1));
        api.stream().playEverything();
        sink.stop();

        assertEquals(suspendedAt + PERIOD, sink.framePosition());
    }

    @Test
    void aFailedReacquireSurfacesOneExceptionAndLeavesTheSinkClosable() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        sink.stop();
        sink.suspend();
        api.failOpensWith(new OutputUnavailableException("device in use"));

        assertThrows(OutputUnavailableException.class, sink::start);

        assertDoesNotThrow(sink::close);
        assertEquals(1, api.opens());
    }

    @Test
    void theSinkCanStartAgainOnceTheDeviceIsFree() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        api.failOpensWith(new OutputUnavailableException("device in use"));
        WasapiExclusiveSink sink = new WasapiExclusiveSink(api, TWENTY_FOUR_BIT);
        assertThrows(OutputUnavailableException.class, sink::start);

        api.recoverOpens();
        sink.start();

        assertEquals(1, api.opens());
    }

    @Test
    void aSubmitFailureSurfacesAndTheDeviceIsClosedOnce() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        api.stream().failSubmitsWith(new OutputUnavailableException("device invalidated"));
        byte[] data = frames24(PERIOD, 1);

        assertThrows(OutputUnavailableException.class, () -> sink.write(data, 0, data.length));
        sink.close();

        assertEquals(1, api.stream().closes());
    }

    @Test
    void aFailureMidWriteReportsTheBytesOfWholePeriodsAlreadySubmitted() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        api.stream().failSubmitsAfter(1, new OutputUnavailableException("device invalidated"));
        byte[] data = frames24(PERIOD * 2 + 2, 1);

        OutputUnavailableException failure = assertThrows(OutputUnavailableException.class,
                () -> sink.write(data, 0, data.length));

        assertEquals(PERIOD * TWENTY_FOUR_BIT.getFrameSize(), failure.consumedBytes());
    }

    @Test
    void aDeviceThatStopsRequestingAudioFailsTheWrite() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);
        api.stream().stopRequestingAudio();
        byte[] data = frames24(PERIOD, 1);

        assertThrows(OutputUnavailableException.class, () -> sink.write(data, 0, data.length));
    }

    @Test
    void closingTwiceClosesTheDeviceOnceAndNeverThrows() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);
        WasapiExclusiveSink sink = started(api, TWENTY_FOUR_BIT);

        assertDoesNotThrow(sink::close);
        assertDoesNotThrow(sink::close);

        assertEquals(1, api.stream().closes());
    }

    @Test
    void aClosedSinkCannotStart() {
        WasapiExclusiveSink sink = new WasapiExclusiveSink(apiWith(SampleContainer.PADDED_32), TWENTY_FOUR_BIT);
        sink.close();

        assertThrows(IllegalStateException.class, sink::start);
    }

    @Test
    void closingAnUnstartedSinkOpensNothing() {
        FakeWasapiApi api = apiWith(SampleContainer.PADDED_32);

        new WasapiExclusiveSink(api, TWENTY_FOUR_BIT).close();

        assertEquals(0, api.opens());
    }
}
