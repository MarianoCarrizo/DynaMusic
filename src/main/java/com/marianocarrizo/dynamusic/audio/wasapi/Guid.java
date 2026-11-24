package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class Guid {

    static final int BYTES = 16;
    private static final int HEX_RADIX = 16;
    private static final int GROUPS = 5;

    private final byte[] bytes;

    Guid(String text) {
        String[] groups = text.split("-");
        if (groups.length != GROUPS) throw new IllegalArgumentException("Not a GUID: " + text);
        ByteBuffer buffer = ByteBuffer.allocate(BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt((int) Long.parseLong(groups[0], HEX_RADIX));
        buffer.putShort((short) Integer.parseInt(groups[1], HEX_RADIX));
        buffer.putShort((short) Integer.parseInt(groups[2], HEX_RADIX));
        String tail = groups[3] + groups[4];
        for (int i = 0; i < tail.length(); i += 2) {
            buffer.put((byte) Integer.parseInt(tail.substring(i, i + 2), HEX_RADIX));
        }
        this.bytes = buffer.array();
    }

    byte[] toBytes() {
        return bytes.clone();
    }

    void writeTo(MemorySegment target, long offset) {
        MemorySegment.copy(MemorySegment.ofArray(bytes), 0, target, offset, BYTES);
    }

    MemorySegment allocate(Arena arena) {
        MemorySegment segment = arena.allocate(BYTES, Integer.BYTES);
        writeTo(segment, 0);
        return segment;
    }
}
