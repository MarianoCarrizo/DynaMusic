package com.marianocarrizo.dynamusic.tags;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public enum TagFormat {
    FLAC(new FlacTagReader()),
    ID3V2(new Id3v2TagReader()),
    OGG(new OggTagReader()),
    MP4(new Mp4TagReader()),
    NONE((in, out) -> { });

    private final TagReader reader;

    TagFormat(TagReader reader) {
        this.reader = reader;
    }

    public TrackTags readTags(File file) {
        return run(file, false).tags();
    }

    public Optional<byte[]> readPicture(File file) {
        return run(file, true).picture();
    }

    TrackTags readTags(ByteSource source) {
        return run(source, false).tags();
    }

    Optional<byte[]> readPicture(ByteSource source) {
        return run(source, true).picture();
    }

    private TagAccumulator run(File file, boolean wantPicture) {
        if (this == NONE) return new TagAccumulator(wantPicture);
        try (ByteSource source = ByteSource.ofFile(file)) {
            return run(source, wantPicture);
        } catch (IOException | RuntimeException unreadable) {
            return new TagAccumulator(wantPicture);
        }
    }

    private TagAccumulator run(ByteSource source, boolean wantPicture) {
        TagAccumulator accumulator = new TagAccumulator(wantPicture);
        try {
            reader.read(new BoundedReader(source), accumulator);
        } catch (IOException | RuntimeException malformed) {
            return accumulator;
        }
        return accumulator;
    }
}
