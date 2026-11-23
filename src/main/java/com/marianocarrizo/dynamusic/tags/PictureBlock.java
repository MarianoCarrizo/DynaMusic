package com.marianocarrizo.dynamusic.tags;

import java.util.Optional;

record PictureBlock(int type, byte[] data) {

    private static final int INT_BYTES = 4;
    private static final int DIMENSION_FIELDS_BYTES = 16;
    private static final int MIN_BLOCK_BYTES = INT_BYTES * 8;

    static Optional<PictureBlock> parse(byte[] block) {
        if (block.length < MIN_BLOCK_BYTES) return Optional.empty();
        long type = Bytes.u32be(block, 0);
        long mimeLength = Bytes.u32be(block, INT_BYTES);
        long descriptionAt = 2L * INT_BYTES + mimeLength;
        if (mimeLength > BoundedReader.MAX_FIELD_BYTES || descriptionAt + INT_BYTES > block.length) {
            return Optional.empty();
        }
        long descriptionLength = Bytes.u32be(block, (int) descriptionAt);
        long dimensionsAt = descriptionAt + INT_BYTES + descriptionLength;
        long dataLengthAt = dimensionsAt + DIMENSION_FIELDS_BYTES;
        if (descriptionLength > BoundedReader.MAX_FIELD_BYTES || dataLengthAt + INT_BYTES > block.length) {
            return Optional.empty();
        }
        long dataLength = Bytes.u32be(block, (int) dataLengthAt);
        long dataAt = dataLengthAt + INT_BYTES;
        if (dataLength > BoundedReader.MAX_PICTURE_BYTES || dataAt + dataLength > block.length) {
            return Optional.empty();
        }
        byte[] data = new byte[(int) dataLength];
        System.arraycopy(block, (int) dataAt, data, 0, data.length);
        return Optional.of(new PictureBlock((int) type, data));
    }
}
