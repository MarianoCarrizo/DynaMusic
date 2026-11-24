package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GuidTest {

    @Test
    void firstThreeGroupsAreLittleEndianAndTheRestKeepTheirOrder() {
        byte[] expected = {
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00, 0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
        };

        assertArrayEquals(expected, new Guid("00000001-0000-0010-8000-00aa00389b71").toBytes());
    }

    @Test
    void uppercaseAndLowercaseHexAreEquivalent() {
        assertArrayEquals(new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E").toBytes(),
                new Guid("bcde0395-e52f-467c-8e3d-c4579291692e").toBytes());
    }

    @Test
    void highBitsOfTheFirstGroupSurvive() {
        byte[] bytes = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E").toBytes();

        assertArrayEquals(new byte[] {(byte) 0x95, 0x03, (byte) 0xDE, (byte) 0xBC}, Arrays.copyOf(bytes, 4));
    }

    @Test
    void textWithoutFiveGroupsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Guid("not-a-guid"));
    }

    @Test
    void allocatedCopyMatchesTheBytes() {
        Guid guid = new Guid("F294ACFC-3146-4483-A7BF-ADDCA7C260E2");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = guid.allocate(arena);

            assertArrayEquals(guid.toBytes(), segment.toArray(ValueLayout.JAVA_BYTE));
        }
    }
}
