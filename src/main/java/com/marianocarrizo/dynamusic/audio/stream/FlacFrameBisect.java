package com.marianocarrizo.dynamusic.audio.stream;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Optional;

final class FlacFrameBisect {

    static final int DEFAULT_SPAN_BYTES = 64 * 1024;
    private static final int READ_BYTES = 128 * 1024;
    private static final int CONFIRM_SPAN_BYTES = 512 * 1024;
    private static final int MIN_FRAME_BYTES = 10;

    private final RandomAccessFile file;
    private final long firstFrameOffset;
    private final int minBlockSize;
    private final int maxBlockSize;
    private final int spanBytes;
    private final byte[] buffer = new byte[READ_BYTES];
    private long fileLength;

    FlacFrameBisect(RandomAccessFile file, long firstFrameOffset, int minBlockSize, int maxBlockSize, int spanBytes) {
        this.file = file;
        this.firstFrameOffset = firstFrameOffset;
        this.minBlockSize = minBlockSize;
        this.maxBlockSize = maxBlockSize;
        this.spanBytes = spanBytes;
    }

    FlacSeekPoint locate(long targetSample) throws IOException {
        fileLength = file.length();
        long low = firstFrameOffset;
        long high = fileLength;
        FlacSeekPoint best = new FlacSeekPoint(0, firstFrameOffset);
        while (high - low > spanBytes) {
            long middle = (low + high) >>> 1;
            Optional<FlacSeekPoint> found = firstHeaderFrom(middle, high);
            if (found.isPresent() && found.get().sample() <= targetSample) {
                best = found.get();
                low = best.offset() + 1;
            } else {
                high = middle;
            }
        }
        return scanForward(Math.max(low, best.offset()), best, targetSample);
    }

    private FlacSeekPoint scanForward(long start, FlacSeekPoint known, long targetSample) throws IOException {
        FlacSeekPoint best = known;
        Optional<FlacSeekPoint> next = firstHeaderFrom(start, fileLength);
        while (next.isPresent() && next.get().sample() <= targetSample) {
            best = next.get();
            next = firstHeaderFrom(best.offset() + 1, fileLength);
        }
        return best;
    }

    Optional<FlacSeekPoint> nextAfter(long offset) throws IOException {
        fileLength = file.length();
        return firstHeaderFrom(offset + 1, fileLength);
    }

    private Optional<FlacSeekPoint> firstHeaderFrom(long from, long limit) throws IOException {
        Optional<FlacSeekPoint> candidate = rawHeaderFrom(from, limit);
        while (candidate.isPresent() && !isConfirmed(candidate.get())) {
            candidate = rawHeaderFrom(candidate.get().offset() + 1, limit);
        }
        return candidate;
    }

    private boolean isConfirmed(FlacSeekPoint candidate) throws IOException {
        long limit = Math.min(fileLength, candidate.offset() + CONFIRM_SPAN_BYTES);
        Optional<FlacSeekPoint> following = rawHeaderFrom(candidate.offset() + MIN_FRAME_BYTES, limit);
        if (following.isEmpty()) return limit >= fileLength;
        while (following.isPresent()) {
            long advance = following.get().sample() - candidate.sample();
            if (advance >= minBlockSize && advance <= maxBlockSize) return true;
            following = rawHeaderFrom(following.get().offset() + 1, limit);
        }
        return false;
    }

    private Optional<FlacSeekPoint> rawHeaderFrom(long from, long limit) throws IOException {
        long cursor = from;
        while (cursor < limit) {
            int read = fill(cursor);
            if (read <= 0) return Optional.empty();
            boolean reachesEnd = cursor + read >= fileLength;
            int scanEnd = reachesEnd ? read : read - FlacFrameHeader.MAX_BYTES;
            for (int i = 0; i < scanEnd && cursor + i < limit; i++) {
                Optional<FlacFrameHeader> header = FlacFrameHeader.parse(buffer, i, minBlockSize);
                if (header.isPresent()) return Optional.of(new FlacSeekPoint(header.get().firstSample(), cursor + i));
            }
            if (reachesEnd) return Optional.empty();
            cursor += Math.max(scanEnd, 1);
        }
        return Optional.empty();
    }

    private int fill(long position) throws IOException {
        file.seek(position);
        int total = 0;
        while (total < buffer.length) {
            int read = file.read(buffer, total, buffer.length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }
}
