package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LinePcmSinkTest {

    @Test
    void bufferHoldsAboutOneHundredMilliseconds() {
        assertEquals(17_640, LinePcmSink.bufferBytes(PcmFormats.signedLittleEndian(16, 44_100f, 2)));
        assertEquals(19_200, LinePcmSink.bufferBytes(PcmFormats.signedLittleEndian(16, 48_000f, 2)));
        assertEquals(38_400, LinePcmSink.bufferBytes(PcmFormats.signedLittleEndian(16, 96_000f, 2)));
    }
}
