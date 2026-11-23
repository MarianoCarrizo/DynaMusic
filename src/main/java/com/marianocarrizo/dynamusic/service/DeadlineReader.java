package com.marianocarrizo.dynamusic.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

final class DeadlineReader {

    private static final int CHUNK_BYTES = 1024;

    private DeadlineReader() {
    }

    static byte[] read(Socket client, int maxBytes, long deadlineMs) throws IOException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMs);
        InputStream in = client.getInputStream();
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] chunk = new byte[CHUNK_BYTES];
        while (collected.size() < maxBytes) {
            client.setSoTimeout(remainingMs(deadlineNanos));
            int read = in.read(chunk, 0, Math.min(chunk.length, maxBytes - collected.size()));
            if (read < 0) break;
            collected.write(chunk, 0, read);
        }
        return collected.toByteArray();
    }

    private static int remainingMs(long deadlineNanos) throws SocketTimeoutException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remaining <= 0) throw new SocketTimeoutException("Connection deadline exceeded");
        return (int) remaining;
    }
}
