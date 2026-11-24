package com.marianocarrizo.dynamusic.audio.stream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.util.Optional;
import javax.sound.sampled.AudioFormat;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

public final class FlacPcmSource implements PcmSource {

    private static final int INPUT_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_BYTES_PER_SAMPLE = 4;
    private static final int MAX_RESYNCS = 4;

    private final File file;
    private final RandomAccessFile random;
    private final FlacMetadata metadata;
    private final FlacFrameBisect bisect;
    private final AudioFormat format;
    private final int frameBytes;
    private final long totalFrames;
    private ByteData decoded;
    private int decodedOffset;
    private int decodedLength;
    private long discardBytes;
    private FLACDecoder decoder;
    private InputStream decoderInput;
    private long seekTarget;
    private long seekOffset;
    private long expectedSample;
    private int resyncs;
    private boolean awaitingFirstFrame;

    private FlacPcmSource(File file, RandomAccessFile random, FlacMetadata metadata) throws IOException {
        this.file = file;
        this.random = random;
        this.metadata = metadata;
        StreamInfo info = metadata.streamInfo();
        if (!SampleWidth.isSupported(info.getBitsPerSample())) {
            throw new IOException("Unsupported FLAC sample size: " + info.getBitsPerSample());
        }
        this.format = PcmFormats.signedLittleEndian(info.getBitsPerSample(), info.getSampleRate(), info.getChannels());
        this.frameBytes = format.getFrameSize();
        this.totalFrames = info.getTotalSamples();
        this.bisect = new FlacFrameBisect(random, metadata.firstFrameOffset(), info.getMinBlockSize(),
                info.getMaxBlockSize(), FlacFrameBisect.DEFAULT_SPAN_BYTES);
        this.decoded = new ByteData(info.getMaxBlockSize() * info.getChannels() * MAX_BYTES_PER_SAMPLE);
        openDecoderAt(metadata.firstFrameOffset());
    }

    public static FlacPcmSource open(File file) throws IOException {
        RandomAccessFile random = new RandomAccessFile(file, "r");
        try {
            return new FlacPcmSource(file, random, FlacMetadata.read(random));
        } catch (IOException | RuntimeException failure) {
            random.close();
            throw failure;
        }
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public long totalFrames() {
        return totalFrames;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        if (!ensureDecoded()) return END_OF_STREAM;
        int count = Math.min(buffer.length - buffer.length % frameBytes, decodedLength - decodedOffset);
        System.arraycopy(decoded.getData(), decodedOffset, buffer, 0, count);
        decodedOffset += count;
        return count;
    }

    @Override
    public void seekToFrame(long frame) throws IOException {
        long target = Math.max(0, totalFrames > 0 ? Math.min(frame, totalFrames) : frame);
        FlacSeekIndex index = metadata.seekIndex();
        FlacSeekPoint point = index.hasPoints() ? index.locate(target) : bisect.locate(target);
        openDecoderAt(point.offset());
        seekTarget = target;
        seekOffset = point.offset();
        expectedSample = point.sample();
        resyncs = 0;
        awaitingFirstFrame = true;
        discardBytes = (target - point.sample()) * frameBytes;
        decodedOffset = 0;
        decodedLength = 0;
    }

    @Override
    public void close() {
        closeDecoderInput();
        try {
            random.close();
        } catch (IOException alreadyClosed) {
            return;
        }
    }

    private boolean ensureDecoded() throws IOException {
        while (decodedOffset >= decodedLength) {
            if (!decodeNextFrame()) return false;
            awaitingFirstFrame = false;
            decodedLength = decoded.getLen();
            int skipped = (int) Math.min(discardBytes, decodedLength);
            decodedOffset = skipped;
            discardBytes -= skipped;
        }
        return true;
    }

    private boolean decodeNextFrame() throws IOException {
        while (true) {
            try {
                Frame frame = decoder.readNextFrame();
                if (frame != null && (!awaitingFirstFrame || frame.header.sampleNumber == expectedSample)) {
                    decoded = decoder.decodeFrame(frame, decoded);
                    return true;
                }
                if (!awaitingFirstFrame || !resyncAfterBadSeek()) {
                    if (frame != null) throw new IOException("Seek landed on a misaligned FLAC frame");
                    return false;
                }
            } catch (IOException | RuntimeException failure) {
                if (!awaitingFirstFrame || !resyncAfterBadSeek()) throw failure;
            }
        }
    }

    private boolean resyncAfterBadSeek() throws IOException {
        if (resyncs >= MAX_RESYNCS) return false;
        resyncs++;
        Optional<FlacSeekPoint> next = nextCandidate();
        if (next.isEmpty()) return false;
        seekOffset = next.get().offset();
        expectedSample = next.get().sample();
        openDecoderAt(seekOffset);
        discardBytes = Math.max(0, seekTarget - expectedSample) * frameBytes;
        return true;
    }

    private Optional<FlacSeekPoint> nextCandidate() throws IOException {
        FlacSeekPoint located = bisect.locate(seekTarget);
        return located.offset() != seekOffset ? Optional.of(located) : bisect.nextAfter(seekOffset);
    }

    private void openDecoderAt(long offset) throws IOException {
        closeDecoderInput();
        FileInputStream stream = new FileInputStream(file);
        try {
            stream.getChannel().position(offset);
            decoderInput = new SequenceInputStream(new ByteArrayInputStream(metadata.decoderPrefix()),
                    new BufferedInputStream(stream, INPUT_BUFFER_BYTES));
            decoder = new FLACDecoder(decoderInput);
            decoder.readMetadata(decoder.readStreamInfo());
        } catch (IOException | RuntimeException failure) {
            closeDecoderInput();
            stream.close();
            throw failure;
        }
    }

    private void closeDecoderInput() {
        InputStream input = decoderInput;
        decoderInput = null;
        if (input == null) return;
        try {
            input.close();
        } catch (IOException alreadyClosed) {
            return;
        }
    }
}
