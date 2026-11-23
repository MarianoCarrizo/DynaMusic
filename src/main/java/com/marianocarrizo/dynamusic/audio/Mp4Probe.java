package com.marianocarrizo.dynamusic.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

final class Mp4Probe {

    static final int MAX_MOOV_BYTES = 8 * 1024 * 1024;
    private static final int HEADER_BYTES = 8;
    private static final int EXTENDED_HEADER_BYTES = 16;
    private static final int MAX_TOP_LEVEL_ATOMS = 4096;
    private static final int SAMPLE_DESCRIPTION_PREFIX_BYTES = 8;
    private static final String MOVIE = "moov";
    private static final String SAMPLE_DESCRIPTION = "stsd";
    private static final String LOSSLESS_ENTRY = "alac";
    private static final String AAC_ENTRY = "mp4a";
    private static final int AUDIO_ENTRY_BYTES = 36;
    private static final int SAMPLE_SIZE_OFFSET = 26;
    private static final int SAMPLE_RATE_OFFSET = 32;
    private static final Set<String> CONTAINERS = Set.of("trak", "mdia", "minf", "stbl");

    private Mp4Probe() {}

    record SampleEntry(String type, int bitsPerSample, int sampleRate) {
        boolean isLossless() {
            return LOSSLESS_ENTRY.equals(type);
        }
    }

    static Optional<SampleEntry> sampleEntry(File file) {
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            byte[] movie = readMovieAtom(source);
            return movie == null ? Optional.empty() : findSampleEntry(movie, 0, movie.length);
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static Optional<SampleEntry> findSampleEntry(byte[] atoms, int start, int end) {
        int position = start;
        while (position + HEADER_BYTES <= end) {
            long size = unsigned32(atoms, position);
            String type = new String(atoms, position + 4, 4, StandardCharsets.ISO_8859_1);
            if (size < HEADER_BYTES || position + size > end) return Optional.empty();
            int contentStart = position + HEADER_BYTES;
            int atomEnd = (int) (position + size);
            if (CONTAINERS.contains(type)) {
                Optional<SampleEntry> nested = findSampleEntry(atoms, contentStart, atomEnd);
                if (nested.isPresent()) return nested;
            }
            if (SAMPLE_DESCRIPTION.equals(type)) {
                Optional<SampleEntry> entry = firstAudioEntry(atoms, contentStart, atomEnd);
                if (entry.isPresent()) return entry;
            }
            position = atomEnd;
        }
        return Optional.empty();
    }

    private static Optional<SampleEntry> firstAudioEntry(byte[] atoms, int start, int end) {
        int entry = start + SAMPLE_DESCRIPTION_PREFIX_BYTES;
        if (entry + AUDIO_ENTRY_BYTES > end) return Optional.empty();
        String format = new String(atoms, entry + 4, 4, StandardCharsets.ISO_8859_1);
        if (!LOSSLESS_ENTRY.equals(format) && !AAC_ENTRY.equals(format)) return Optional.empty();
        int bits = ((atoms[entry + SAMPLE_SIZE_OFFSET] & 0xFF) << 8) | (atoms[entry + SAMPLE_SIZE_OFFSET + 1] & 0xFF);
        int rate = ((atoms[entry + SAMPLE_RATE_OFFSET] & 0xFF) << 8) | (atoms[entry + SAMPLE_RATE_OFFSET + 1] & 0xFF);
        return Optional.of(new SampleEntry(format, LOSSLESS_ENTRY.equals(format) ? bits : 0, rate));
    }

    static boolean isAlac(File file) {
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            byte[] movie = readMovieAtom(source);
            return movie != null && containsLosslessEntry(movie, 0, movie.length);
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private static byte[] readMovieAtom(RandomAccessFile source) throws IOException {
        long position = 0;
        long length = source.length();
        for (int atoms = 0; atoms < MAX_TOP_LEVEL_ATOMS && position + HEADER_BYTES <= length; atoms++) {
            source.seek(position);
            byte[] header = new byte[EXTENDED_HEADER_BYTES];
            source.readFully(header, 0, HEADER_BYTES);
            long size = unsigned32(header, 0);
            long headerBytes = HEADER_BYTES;
            if (size == 1) {
                source.readFully(header, HEADER_BYTES, HEADER_BYTES);
                size = unsigned64(header, HEADER_BYTES);
                headerBytes = EXTENDED_HEADER_BYTES;
            } else if (size == 0) {
                size = length - position;
            }
            if (size < headerBytes || size > length - position) return null;
            if (MOVIE.equals(new String(header, 4, 4, StandardCharsets.ISO_8859_1))) {
                long contentBytes = size - headerBytes;
                if (contentBytes > MAX_MOOV_BYTES) return null;
                byte[] content = new byte[(int) contentBytes];
                source.seek(position + headerBytes);
                source.readFully(content);
                return content;
            }
            position += size;
        }
        return null;
    }

    static boolean containsLosslessEntry(byte[] atoms, int start, int end) {
        int position = start;
        while (position + HEADER_BYTES <= end) {
            long size = unsigned32(atoms, position);
            String type = new String(atoms, position + 4, 4, StandardCharsets.ISO_8859_1);
            if (size < HEADER_BYTES || position + size > end) return false;
            int contentStart = position + HEADER_BYTES;
            int atomEnd = (int) (position + size);
            if (CONTAINERS.contains(type) && containsLosslessEntry(atoms, contentStart, atomEnd)) return true;
            if (SAMPLE_DESCRIPTION.equals(type) && firstEntryIsLossless(atoms, contentStart, atomEnd)) return true;
            position = atomEnd;
        }
        return false;
    }

    private static boolean firstEntryIsLossless(byte[] atoms, int start, int end) {
        int entry = start + SAMPLE_DESCRIPTION_PREFIX_BYTES;
        if (entry + HEADER_BYTES > end) return false;
        return LOSSLESS_ENTRY.equals(new String(atoms, entry + 4, 4, StandardCharsets.ISO_8859_1));
    }

    private static long unsigned32(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 4; i++) value = (value << 8) | (bytes[offset + i] & 0xFF);
        return value;
    }

    private static long unsigned64(byte[] bytes, int offset) {
        return (unsigned32(bytes, offset) << 32) | unsigned32(bytes, offset + 4);
    }
}
