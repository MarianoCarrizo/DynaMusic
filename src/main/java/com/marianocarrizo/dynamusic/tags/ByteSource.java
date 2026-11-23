package com.marianocarrizo.dynamusic.tags;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

interface ByteSource extends Closeable {

    long length() throws IOException;

    void readAt(long position, byte[] target, int count) throws IOException;

    static ByteSource ofBytes(byte[] bytes) {
        return new ArraySource(bytes);
    }

    static ByteSource ofFile(File file) throws IOException {
        return new FileSource(new RandomAccessFile(file, "r"));
    }

    final class ArraySource implements ByteSource {
        private final byte[] bytes;

        ArraySource(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public long length() {
            return bytes.length;
        }

        @Override
        public void readAt(long position, byte[] target, int count) throws IOException {
            if (position < 0 || count < 0 || position + count > bytes.length) throw new EOFException();
            System.arraycopy(bytes, (int) position, target, 0, count);
        }

        @Override
        public void close() {
        }
    }

    final class FileSource implements ByteSource {
        private final RandomAccessFile file;

        FileSource(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public long length() throws IOException {
            return file.length();
        }

        @Override
        public void readAt(long position, byte[] target, int count) throws IOException {
            file.seek(position);
            file.readFully(target, 0, count);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }
}
