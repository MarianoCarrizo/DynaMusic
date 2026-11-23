package com.marianocarrizo.dynamusic.tags;

final class Bytes {

    private static final int BYTE_MASK = 0xFF;
    private static final int SYNCHSAFE_MASK = 0x7F;
    private static final int HIGH_BIT = 0x80;
    private static final int BITS_PER_BYTE = 8;
    private static final int BITS_PER_SYNCHSAFE_BYTE = 7;
    private static final int INT_BYTES = 4;

    private Bytes() {}

    static int u16be(byte[] bytes, int offset) {
        return (u8(bytes, offset) << BITS_PER_BYTE) | u8(bytes, offset + 1);
    }

    static int u24be(byte[] bytes, int offset) {
        return (u16be(bytes, offset) << BITS_PER_BYTE) | u8(bytes, offset + 2);
    }

    static long u32be(byte[] bytes, int offset) {
        return ((long) u16be(bytes, offset) << 16) | u16be(bytes, offset + 2);
    }

    static long u32le(byte[] bytes, int offset) {
        return ((long) u8(bytes, offset + 3) << 24) | ((long) u8(bytes, offset + 2) << 16)
                | ((long) u8(bytes, offset + 1) << BITS_PER_BYTE) | u8(bytes, offset);
    }

    static long u64be(byte[] bytes, int offset) {
        return (u32be(bytes, offset) << 32) | u32be(bytes, offset + INT_BYTES);
    }

    static long synchsafe32(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < INT_BYTES; i++) {
            value = (value << BITS_PER_SYNCHSAFE_BYTE) | (bytes[offset + i] & SYNCHSAFE_MASK);
        }
        return value;
    }

    static boolean isSynchsafe(byte[] bytes, int offset) {
        for (int i = 0; i < INT_BYTES; i++) {
            if ((bytes[offset + i] & HIGH_BIT) != 0) return false;
        }
        return true;
    }

    static boolean startsWith(byte[] bytes, int offset, String ascii) {
        if (offset < 0 || offset + ascii.length() > bytes.length) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if (bytes[offset + i] != ascii.charAt(i)) return false;
        }
        return true;
    }

    private static int u8(byte[] bytes, int offset) {
        return bytes[offset] & BYTE_MASK;
    }
}
