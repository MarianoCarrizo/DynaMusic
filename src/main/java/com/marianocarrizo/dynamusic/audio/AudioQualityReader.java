package com.marianocarrizo.dynamusic.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;

public final class AudioQualityReader {

    static final int HEAD_BYTES = 4096;
    static final int OPUS_OUTPUT_RATE = 48000;
    private static final int BITS_PER_KILOBIT = 1000;
    private static final int ID3_HEADER_BYTES = 10;
    private static final int ID3_FOOTER_BYTES = 10;
    private static final int ID3_FOOTER_FLAG = 0x10;
    private static final int OGG_HEADER_BYTES = 27;
    private static final int OGG_SEGMENT_COUNT_OFFSET = 26;
    private static final int VORBIS_RATE_OFFSET = 12;
    private static final int VORBIS_NOMINAL_BITRATE_OFFSET = 20;
    private static final String VORBIS_IDENTIFICATION = "\u0001vorbis";
    private static final String OPUS_IDENTIFICATION = "OpusHead";
    private static final String OGG_CAPTURE = "OggS";
    private static final String ID3 = "ID3";
    private static final int[] MPEG1_LAYER3_KBPS = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
    private static final int[] MPEG2_LAYER3_KBPS = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};
    private static final int MPEG1 = 3;
    private static final int LAYER3 = 1;
    private static final int VARIABLE_BITRATE_SCAN_BYTES = 64;
    private static final String LOSSLESS_LABEL = "ALAC";

    private AudioQualityReader() {}

    public static Optional<AudioQuality> read(File file) {
        Optional<AudioFormat> format = AudioFormat.fromFile(file);
        if (format.isEmpty()) return Optional.empty();
        try {
            return Optional.of(switch (format.get()) {
                case WAV, AIFF, FLAC -> readPcm(file, format.get());
                case MP3 -> readMp3(file);
                case M4A -> readMp4(file);
                case OGG, OPUS -> readOgg(file, format.get());
                default -> AudioQuality.ofFormat(format.get().name());
            });
        } catch (IOException | RuntimeException unreadable) {
            return Optional.of(AudioQuality.ofFormat(format.get().name()));
        }
    }

    static AudioQuality fromPcmHeader(AudioFormat format, byte[] head) {
        return PcmProbe.parse(format, head)
                .map(info -> new AudioQuality(format.name(), info.bitsPerSample(), info.floatingPoint(),
                        info.sampleRate(), 0))
                .orElse(AudioQuality.ofFormat(format.name()));
    }

    static AudioQuality fromMp3Frame(byte[] bytes) {
        for (int i = 0; i + 4 <= bytes.length; i++) {
            if ((bytes[i] & 0xFF) != 0xFF || (bytes[i + 1] & 0xE0) != 0xE0) continue;
            int version = (bytes[i + 1] >> 3) & 3;
            int layer = (bytes[i + 1] >> 1) & 3;
            int bitrateIndex = (bytes[i + 2] >> 4) & 0xF;
            int rateIndex = (bytes[i + 2] >> 2) & 3;
            boolean valid = version != 1 && layer == LAYER3 && bitrateIndex != 0 && bitrateIndex != 0xF && rateIndex != 3;
            if (!valid) continue;
            int[] table = version == MPEG1 ? MPEG1_LAYER3_KBPS : MPEG2_LAYER3_KBPS;
            boolean variable = isVariableBitrate(bytes, i + 4);
            return new AudioQuality(AudioFormat.MP3.name(), 0, false, 0, variable ? 0 : table[bitrateIndex]);
        }
        return AudioQuality.ofFormat(AudioFormat.MP3.name());
    }

    static AudioQuality fromOggPage(AudioFormat format, byte[] head) {
        if (!Bytes.startsWith(head, 0, OGG_CAPTURE) || head.length <= OGG_SEGMENT_COUNT_OFFSET) {
            return AudioQuality.ofFormat(format.name());
        }
        int dataStart = OGG_HEADER_BYTES + (head[OGG_SEGMENT_COUNT_OFFSET] & 0xFF);
        if (Bytes.startsWith(head, dataStart, OPUS_IDENTIFICATION)) {
            return new AudioQuality(format.name(), 0, false, OPUS_OUTPUT_RATE, 0);
        }
        if (Bytes.startsWith(head, dataStart, VORBIS_IDENTIFICATION) && head.length >= dataStart + 24) {
            long rate = Bytes.u32le(head, dataStart + VORBIS_RATE_OFFSET);
            int nominal = (int) Bytes.u32le(head, dataStart + VORBIS_NOMINAL_BITRATE_OFFSET);
            int kbps = nominal > 0 ? nominal / BITS_PER_KILOBIT : 0;
            return new AudioQuality(format.name(), 0, false, (int) Math.min(rate, Integer.MAX_VALUE), kbps);
        }
        return AudioQuality.ofFormat(format.name());
    }

    private static boolean isVariableBitrate(byte[] bytes, int from) {
        int end = Math.min(bytes.length, from + VARIABLE_BITRATE_SCAN_BYTES);
        for (int i = from; i + 4 <= end; i++) {
            if (Bytes.startsWith(bytes, i, "Xing") || Bytes.startsWith(bytes, i, "VBRI")) return true;
        }
        return false;
    }

    private static AudioQuality readPcm(File file, AudioFormat format) throws IOException {
        return fromPcmHeader(format, readHead(file, 0));
    }

    private static AudioQuality readOgg(File file, AudioFormat format) throws IOException {
        return fromOggPage(format, readHead(file, 0));
    }

    private static AudioQuality readMp3(File file) throws IOException {
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            long audioStart = 0;
            byte[] tag = new byte[ID3_HEADER_BYTES];
            if (source.length() >= ID3_HEADER_BYTES) {
                source.readFully(tag);
                if (Bytes.startsWith(tag, 0, ID3)) {
                    boolean footer = (tag[5] & ID3_FOOTER_FLAG) != 0;
                    audioStart = ID3_HEADER_BYTES + Bytes.synchsafe32(tag, 6) + (footer ? ID3_FOOTER_BYTES : 0);
                }
            }
            return fromMp3Frame(readHeadFrom(source, audioStart));
        }
    }

    private static AudioQuality readMp4(File file) {
        return Mp4Probe.sampleEntry(file)
                .map(entry -> new AudioQuality(entry.isLossless() ? LOSSLESS_LABEL : AudioFormat.M4A.name(), entry.bitsPerSample(), false,
                        entry.sampleRate(), 0))
                .orElse(AudioQuality.ofFormat(AudioFormat.M4A.name()));
    }

    private static byte[] readHead(File file, long offset) throws IOException {
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            return readHeadFrom(source, offset);
        }
    }

    private static byte[] readHeadFrom(RandomAccessFile source, long offset) throws IOException {
        long available = source.length() - offset;
        if (offset < 0 || available <= 0) return new byte[0];
        byte[] head = new byte[(int) Math.min(HEAD_BYTES, available)];
        source.seek(offset);
        source.readFully(head);
        return head;
    }

    private static final class Bytes {
        private Bytes() {}

        static boolean startsWith(byte[] bytes, int offset, String ascii) {
            if (offset < 0 || offset + ascii.length() > bytes.length) return false;
            for (int i = 0; i < ascii.length(); i++) {
                if (bytes[offset + i] != ascii.charAt(i)) return false;
            }
            return true;
        }

        static long u32le(byte[] bytes, int offset) {
            long value = 0;
            for (int i = 3; i >= 0; i--) value = (value << 8) | (bytes[offset + i] & 0xFF);
            return value;
        }

        static long synchsafe32(byte[] bytes, int offset) {
            long value = 0;
            for (int i = 0; i < 4; i++) value = (value << 7) | (bytes[offset + i] & 0x7F);
            return value;
        }
    }
}
