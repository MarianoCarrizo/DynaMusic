package com.marianocarrizo.dynamusic.tags;

import java.io.IOException;
import java.util.Arrays;

final class FlacTagReader implements TagReader {

    private static final byte[] STREAM_MARKER = {0x66, 0x4C, 0x61, 0x43};
    private static final int BLOCK_HEADER_BYTES = 4;
    private static final int LAST_BLOCK_FLAG = 0x80;
    private static final int BLOCK_TYPE_MASK = 0x7F;
    private static final int VORBIS_COMMENT = 4;
    private static final int PICTURE = 6;
    private static final int PICTURE_OVERHEAD_BYTES = 64 * 1024;

    @Override
    public void read(BoundedReader in, TagAccumulator out) throws IOException {
        if (!Arrays.equals(in.read(STREAM_MARKER.length, STREAM_MARKER.length), STREAM_MARKER)) return;
        boolean last = false;
        while (!last) {
            byte[] header = in.read(BLOCK_HEADER_BYTES, BLOCK_HEADER_BYTES);
            last = (header[0] & LAST_BLOCK_FLAG) != 0;
            int type = header[0] & BLOCK_TYPE_MASK;
            long length = Bytes.u24be(header, 1);
            if (length > in.remaining()) return;
            if (type == VORBIS_COMMENT) {
                VorbisComments.parse(in.read(length, BoundedReader.MAX_METADATA_BYTES), out);
            } else if (type == PICTURE) {
                readPicture(in, length, out);
            } else {
                in.skip(length);
            }
        }
    }

    private void readPicture(BoundedReader in, long length, TagAccumulator out) throws IOException {
        long limit = BoundedReader.MAX_PICTURE_BYTES + PICTURE_OVERHEAD_BYTES;
        if (!out.wantsPicture() || length > limit) {
            out.markPicture();
            in.skip(length);
            return;
        }
        PictureBlock.parse(in.read(length, limit)).ifPresent(picture -> out.offerPicture(picture.type(), picture.data()));
    }
}
