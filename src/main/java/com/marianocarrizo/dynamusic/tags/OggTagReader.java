package com.marianocarrizo.dynamusic.tags;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

final class OggTagReader implements TagReader {

    private static final String CAPTURE_PATTERN = "OggS";
    private static final int PAGE_HEADER_BYTES = 27;
    private static final int SEGMENT_COUNT_OFFSET = 26;
    private static final int MAX_SEGMENTS = 255;
    private static final int PACKETS_NEEDED = 2;
    private static final String VORBIS_IDENTIFICATION = "\u0001vorbis";
    private static final String VORBIS_COMMENT_HEADER = "\u0003vorbis";
    private static final String OPUS_IDENTIFICATION = "OpusHead";
    private static final String OPUS_COMMENT_HEADER = "OpusTags";

    @Override
    public void read(BoundedReader in, TagAccumulator out) throws IOException {
        byte[][] packets = readFirstPackets(in);
        if (packets == null) return;
        String commentHeader = commentHeaderFor(packets[0]);
        if (commentHeader == null || !Bytes.startsWith(packets[1], 0, commentHeader)) return;
        VorbisComments.parse(Arrays.copyOfRange(packets[1], commentHeader.length(), packets[1].length), out);
    }

    private static String commentHeaderFor(byte[] identification) {
        if (Bytes.startsWith(identification, 0, VORBIS_IDENTIFICATION)) return VORBIS_COMMENT_HEADER;
        if (Bytes.startsWith(identification, 0, OPUS_IDENTIFICATION)) return OPUS_COMMENT_HEADER;
        return null;
    }

    private static byte[][] readFirstPackets(BoundedReader in) throws IOException {
        byte[][] packets = new byte[PACKETS_NEEDED][];
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        int completed = 0;
        while (completed < PACKETS_NEEDED) {
            byte[] header = in.read(PAGE_HEADER_BYTES, PAGE_HEADER_BYTES);
            if (!Bytes.startsWith(header, 0, CAPTURE_PATTERN)) return null;
            int segmentCount = header[SEGMENT_COUNT_OFFSET] & 0xFF;
            byte[] table = in.read(segmentCount, MAX_SEGMENTS);
            long dataLength = 0;
            for (byte lacing : table) dataLength += lacing & 0xFF;
            byte[] data = in.read(dataLength, BoundedReader.MAX_METADATA_BYTES);
            int offset = 0;
            for (byte lacing : table) {
                int segment = lacing & 0xFF;
                current.write(data, offset, segment);
                offset += segment;
                if (segment == MAX_SEGMENTS) continue;
                packets[completed++] = current.toByteArray();
                current.reset();
                if (completed == PACKETS_NEEDED) break;
            }
        }
        return packets;
    }
}
