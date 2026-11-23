package com.marianocarrizo.dynamusic.tags;

import java.io.EOFException;
import java.io.IOException;

final class BoundedReader {

    static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;
    static final int MAX_PICTURE_BYTES = 8 * 1024 * 1024;
    static final int MAX_FIELD_BYTES = 64 * 1024;

    private final ByteSource source;
    private final long length;
    private long position;
    private long consumed;

    BoundedReader(ByteSource source) throws IOException {
        this.source = source;
        this.length = source.length();
    }

    long length() {
        return length;
    }

    long position() {
        return position;
    }

    long remaining() {
        return length - position;
    }

    void seek(long target) throws IOException {
        if (target < 0 || target > length) throw new EOFException();
        position = target;
    }

    void skip(long count) throws IOException {
        if (count < 0 || count > remaining()) throw new EOFException();
        position += count;
    }

    byte[] read(long count, long limit) throws IOException {
        if (count < 0 || count > limit) throw new TagLimitException("field too large");
        if (count > remaining()) throw new EOFException();
        if (consumed + count > MAX_METADATA_BYTES) throw new TagLimitException("metadata budget exhausted");
        byte[] bytes = new byte[(int) count];
        source.readAt(position, bytes, bytes.length);
        position += count;
        consumed += count;
        return bytes;
    }
}
