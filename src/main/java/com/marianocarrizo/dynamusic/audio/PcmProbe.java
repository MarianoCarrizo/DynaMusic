package com.marianocarrizo.dynamusic.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Optional;

public final class PcmProbe {

    static final int MAX_NATIVE_BITS = 24;
    static final int MAX_NATIVE_SAMPLE_RATE = 96000;
    private static final int HEAD_BYTES = 64 * 1024;
    private static final int CHUNK_HEADER_BYTES = 8;
    private static final int RIFF_HEADER_BYTES = 12;
    private static final int WAVE_FORMAT_FLOAT = 3;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    private static final int EXTENSIBLE_SUBFORMAT_OFFSET = 24;
    private static final int EXTENDED_FLOAT_BYTES = 10;
    private static final int EXTENDED_EXPONENT_BIAS = 16383;
    private static final int MANTISSA_BITS = 63;
    private static final byte[] FLAC_MARKER = {0x66, 0x4C, 0x61, 0x43};
    private static final int FLAC_STREAM_INFO_OFFSET = 8;
    private static final int FLAC_RATE_OFFSET = FLAC_STREAM_INFO_OFFSET + 10;

    public record Info(int bitsPerSample, int sampleRate, boolean floatingPoint) {
        public boolean exceedsNativeDepth() {
            return !floatingPoint && bitsPerSample > MAX_NATIVE_BITS;
        }

        public boolean exceedsNativeRate() {
            return sampleRate > MAX_NATIVE_SAMPLE_RATE;
        }
    }

    private PcmProbe() {}

    public static Optional<Info> read(File file, AudioFormat format) {
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            byte[] head = new byte[(int) Math.min(HEAD_BYTES, source.length())];
            source.readFully(head);
            return parse(format, head);
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static Optional<Info> parse(AudioFormat format, byte[] head) {
        return switch (format) {
            case WAV -> parseWav(head);
            case AIFF -> parseAiff(head);
            case FLAC -> parseFlac(head);
            default -> Optional.empty();
        };
    }

    private static Optional<Info> parseWav(byte[] head) {
        if (!startsWith(head, 0, "RIFF") || !startsWith(head, 8, "WAVE")) return Optional.empty();
        int position = RIFF_HEADER_BYTES;
        while (position + CHUNK_HEADER_BYTES <= head.length) {
            long size = littleEndian(head, position + 4, 4);
            int body = position + CHUNK_HEADER_BYTES;
            if (startsWith(head, position, "fmt ")) return parseWavFormat(head, body, size);
            position = nextChunk(body, size);
            if (position < 0) return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<Info> parseWavFormat(byte[] head, int body, long size) {
        if (size < 16 || body + 16 > head.length) return Optional.empty();
        int code = (int) littleEndian(head, body, 2);
        int rate = (int) littleEndian(head, body + 4, 4);
        int bits = (int) littleEndian(head, body + 14, 2);
        if (code == WAVE_FORMAT_EXTENSIBLE && body + EXTENSIBLE_SUBFORMAT_OFFSET + 2 <= head.length) {
            code = (int) littleEndian(head, body + EXTENSIBLE_SUBFORMAT_OFFSET, 2);
        }
        return Optional.of(new Info(bits, rate, code == WAVE_FORMAT_FLOAT));
    }

    private static Optional<Info> parseAiff(byte[] head) {
        if (!startsWith(head, 0, "FORM")) return Optional.empty();
        int position = RIFF_HEADER_BYTES;
        while (position + CHUNK_HEADER_BYTES <= head.length) {
            long size = bigEndian(head, position + 4, 4);
            int body = position + CHUNK_HEADER_BYTES;
            if (startsWith(head, position, "COMM")) return parseAiffCommon(head, body, size);
            position = nextChunk(body, size);
            if (position < 0) return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<Info> parseAiffCommon(byte[] head, int body, long size) {
        if (size < 18 || body + 18 > head.length) return Optional.empty();
        int bits = (int) bigEndian(head, body + 6, 2);
        int rate = (int) extendedToDouble(Arrays.copyOfRange(head, body + 8, body + 8 + EXTENDED_FLOAT_BYTES));
        boolean floating = size >= 22 && body + 22 <= head.length
                && (startsWith(head, body + 18, "fl32") || startsWith(head, body + 18, "fl64"));
        return Optional.of(new Info(bits, rate, floating));
    }

    private static Optional<Info> parseFlac(byte[] head) {
        if (head.length < FLAC_RATE_OFFSET + 4 || !Arrays.equals(Arrays.copyOf(head, FLAC_MARKER.length), FLAC_MARKER)) return Optional.empty();
        int rate = ((head[FLAC_RATE_OFFSET] & 0xFF) << 12) | ((head[FLAC_RATE_OFFSET + 1] & 0xFF) << 4)
                | ((head[FLAC_RATE_OFFSET + 2] & 0xFF) >> 4);
        int bits = (((head[FLAC_RATE_OFFSET + 2] & 1) << 4) | ((head[FLAC_RATE_OFFSET + 3] & 0xFF) >> 4)) + 1;
        return Optional.of(new Info(bits, rate, false));
    }

    private static int nextChunk(int body, long size) {
        long next = body + size + (size % 2);
        return next > Integer.MAX_VALUE ? -1 : (int) next;
    }

    private static double extendedToDouble(byte[] extended) {
        int exponent = ((extended[0] & 0x7F) << 8) | (extended[1] & 0xFF);
        long mantissa = bigEndian(extended, 2, 8);
        if (exponent == 0 && mantissa == 0) return 0;
        double unsignedMantissa = (mantissa >>> 1) * 2.0 + (mantissa & 1);
        return unsignedMantissa * Math.pow(2, exponent - EXTENDED_EXPONENT_BIAS - MANTISSA_BITS);
    }

    private static boolean startsWith(byte[] bytes, int offset, String ascii) {
        if (offset < 0 || offset + ascii.length() > bytes.length) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if (bytes[offset + i] != ascii.charAt(i)) return false;
        }
        return true;
    }

    private static long littleEndian(byte[] bytes, int offset, int count) {
        long value = 0;
        for (int i = count - 1; i >= 0; i--) value = (value << 8) | (bytes[offset + i] & 0xFF);
        return value;
    }

    private static long bigEndian(byte[] bytes, int offset, int count) {
        long value = 0;
        for (int i = 0; i < count; i++) value = (value << 8) | (bytes[offset + i] & 0xFF);
        return value;
    }
}
