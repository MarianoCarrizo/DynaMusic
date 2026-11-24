package com.marianocarrizo.dynamusic.audio.stream;

final class Crc8 {

    private static final int POLYNOMIAL = 0x07;
    private static final int TOP_BIT = 0x80;
    private static final int BYTE_MASK = 0xFF;
    private static final int BITS_PER_BYTE = 8;
    private static final int[] TABLE = buildTable();

    private Crc8() {
    }

    static int compute(byte[] data, int from, int to) {
        int crc = 0;
        for (int i = from; i < to; i++) {
            crc = TABLE[crc ^ (data[i] & BYTE_MASK)];
        }
        return crc;
    }

    private static int[] buildTable() {
        int[] table = new int[BYTE_MASK + 1];
        for (int value = 0; value < table.length; value++) {
            int crc = value;
            for (int bit = 0; bit < BITS_PER_BYTE; bit++) {
                crc = (crc & TOP_BIT) != 0 ? ((crc << 1) ^ POLYNOMIAL) & BYTE_MASK : (crc << 1) & BYTE_MASK;
            }
            table[value] = crc;
        }
        return table;
    }
}
