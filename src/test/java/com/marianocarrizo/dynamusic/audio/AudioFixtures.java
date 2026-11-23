package com.marianocarrizo.dynamusic.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class AudioFixtures {

    private static final int WAVE_FORMAT_PCM = 1;
    private static final int WAVE_FORMAT_FLOAT = 3;

    private AudioFixtures() {}

    static byte[] wavHeader(int bits, int rate, int channels, boolean floating, int dataBytes) {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataBytes)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) (floating ? WAVE_FORMAT_FLOAT : WAVE_FORMAT_PCM)).putShort((short) channels)
                .putInt(rate).putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8))
                .putShort((short) bits);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);
        return header.array();
    }

    static byte[] wav(int bits, int rate, int channels, boolean floating) {
        int dataBytes = rate / 10 * channels * bits / 8;
        return concat(wavHeader(bits, rate, channels, floating, dataBytes), new byte[dataBytes]);
    }

    static byte[] aiff(int bits, int rate) {
        long mantissa = (long) rate << (63 - exponentOf(rate));
        int exponent = 16383 + exponentOf(rate);
        ByteBuffer common = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN);
        common.put("COMM".getBytes(StandardCharsets.US_ASCII)).putInt(18).putShort((short) 2).putInt(100)
                .putShort((short) bits).putShort((short) exponent).putLong(mantissa);
        ByteBuffer form = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        form.put("FORM".getBytes(StandardCharsets.US_ASCII)).putInt(4 + 26)
                .put("AIFF".getBytes(StandardCharsets.US_ASCII));
        return concat(form.array(), common.array());
    }

    static byte[] flacHeader(int rate, int bits) {
        byte[] streamInfo = new byte[34];
        streamInfo[10] = (byte) (rate >> 12);
        streamInfo[11] = (byte) (rate >> 4);
        streamInfo[12] = (byte) (((rate & 0xF) << 4) | (1 << 1) | (((bits - 1) >> 4) & 1));
        streamInfo[13] = (byte) (((bits - 1) & 0xF) << 4);
        return concat(new byte[] {0x66, 0x4C, 0x61, 0x43, (byte) 0x80, 0, 0, 34}, streamInfo);
    }

    static byte[] atom(String type, byte[]... children) {
        byte[] content = concat(children);
        return concat(ByteBuffer.allocate(4).putInt(8 + content.length).array(),
                type.getBytes(StandardCharsets.ISO_8859_1), content);
    }

    static byte[] sampleDescription(String entryType) {
        byte[] entry = atom(entryType, new byte[8]);
        return atom("stsd", new byte[4], ByteBuffer.allocate(4).putInt(1).array(), entry);
    }

    static byte[] m4a(String entryType, boolean moovLast) {
        byte[] ftyp = atom("ftyp", "M4A ".getBytes(StandardCharsets.US_ASCII), new byte[4]);
        byte[] mdat = atom("mdat", new byte[4000]);
        byte[] moov = atom("moov", atom("trak", atom("mdia", atom("minf", atom("stbl", sampleDescription(entryType))))));
        return moovLast ? concat(ftyp, mdat, moov) : concat(ftyp, moov, mdat);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) joined.writeBytes(part);
        return joined.toByteArray();
    }

    private static int exponentOf(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }
}
