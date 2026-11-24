package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Crc8Test {

    private static final int CHECK_VALUE = 0xF4;

    @Test
    void standardCheckStringMatchesKnownCrc() {
        byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

        assertEquals(CHECK_VALUE, Crc8.compute(data, 0, data.length));
    }

    @Test
    void emptyRangeIsZero() {
        assertEquals(0, Crc8.compute(new byte[4], 2, 2));
    }
}
