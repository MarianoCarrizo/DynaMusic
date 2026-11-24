package com.marianocarrizo.dynamusic.audio.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import org.jflac.io.BitInputStream;
import org.jflac.metadata.SeekTable;
import org.jflac.metadata.StreamInfo;

record FlacMetadata(StreamInfo streamInfo, FlacSeekIndex seekIndex, long firstFrameOffset, byte[] decoderPrefix) {

    private static final byte[] MARKER = {0x66, 0x4C, 0x61, 0x43};
    private static final int BLOCK_HEADER_BYTES = 4;
    private static final int LAST_BLOCK_FLAG = 0x80;
    private static final int TYPE_MASK = 0x7F;
    private static final int TYPE_STREAM_INFO = 0;
    private static final int TYPE_SEEK_TABLE = 3;
    private static final int LENGTH_MASK = 0xFFFFFF;
    private static final int STREAM_INFO_BYTES = 34;
    private static final int TYPE_SHIFT = 24;

    static FlacMetadata read(RandomAccessFile file) throws IOException {
        file.seek(0);
        byte[] marker = new byte[MARKER.length];
        file.readFully(marker);
        if (!Arrays.equals(marker, MARKER)) throw new IOException("Not a FLAC stream");
        StreamInfo info = null;
        byte[] infoBody = null;
        SeekTable table = null;
        boolean last = false;
        while (!last) {
            int header = file.readInt();
            last = (header & (LAST_BLOCK_FLAG << TYPE_SHIFT)) != 0;
            int type = (header >> TYPE_SHIFT) & TYPE_MASK;
            int length = header & LENGTH_MASK;
            if (type == TYPE_STREAM_INFO || type == TYPE_SEEK_TABLE) {
                byte[] body = new byte[length];
                file.readFully(body);
                BitInputStream bits = new BitInputStream(new ByteArrayInputStream(body));
                if (type == TYPE_STREAM_INFO) {
                    info = new StreamInfo(bits, length, false);
                    infoBody = body;
                } else {
                    table = new SeekTable(bits, length, false);
                }
            } else {
                file.seek(file.getFilePointer() + length);
            }
        }
        if (info == null || infoBody == null || infoBody.length != STREAM_INFO_BYTES) {
            throw new IOException("FLAC stream has no STREAMINFO");
        }
        long firstFrame = file.getFilePointer();
        return new FlacMetadata(info, FlacSeekIndex.fromTable(table, firstFrame), firstFrame, prefix(infoBody));
    }

    private static byte[] prefix(byte[] infoBody) {
        byte[] prefix = new byte[MARKER.length + BLOCK_HEADER_BYTES + infoBody.length];
        System.arraycopy(MARKER, 0, prefix, 0, MARKER.length);
        prefix[MARKER.length] = (byte) (LAST_BLOCK_FLAG | TYPE_STREAM_INFO);
        prefix[MARKER.length + 1] = (byte) (infoBody.length >> 16);
        prefix[MARKER.length + 2] = (byte) (infoBody.length >> 8);
        prefix[MARKER.length + 3] = (byte) infoBody.length;
        System.arraycopy(infoBody, 0, prefix, MARKER.length + BLOCK_HEADER_BYTES, infoBody.length);
        return prefix;
    }
}
