package com.marianocarrizo.dynamusic.tags;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class Mp4TagReader implements TagReader {

    private static final int ATOM_HEADER_BYTES = 8;
    private static final int EXTENDED_SIZE_BYTES = 8;
    private static final int EXTENDED_SIZE_MARKER = 1;
    private static final int META_VERSION_FLAGS_BYTES = 4;
    private static final int DATA_PREFIX_BYTES = 8;
    private static final int FLAGS_MASK = 0xFFFFFF;
    private static final int TRACK_NUMBER_OFFSET = 2;
    private static final int TRACK_PAYLOAD_BYTES = 6;
    private static final int MOOV = fourCc("moov");
    private static final int UDTA = fourCc("udta");
    private static final int META = fourCc("meta");
    private static final int ILST = fourCc("ilst");
    private static final int DATA = fourCc("data");
    private static final int TRKN = fourCc("trkn");
    private static final int COVR = fourCc("covr");
    private static final int TITLE = 0xA96E616D;
    private static final int ARTIST = 0xA9415254;
    private static final int ALBUM = 0xA9616C62;
    private static final int DAY = 0xA9646179;
    private static final int JPEG_FLAG = 13;
    private static final int PNG_FLAG = 14;

    private record Atom(int type, long start, long headerBytes, long size) {
        long contentStart() {
            return start + headerBytes;
        }

        long end() {
            return start + size;
        }
    }

    @Override
    public void read(BoundedReader in, TagAccumulator out) throws IOException {
        Optional<Atom> moov = find(in, 0, in.length(), MOOV);
        Optional<Atom> udta = moov.isEmpty() ? Optional.empty() : find(in, moov.get().contentStart(), moov.get().end(), UDTA);
        Optional<Atom> meta = udta.isEmpty() ? Optional.empty() : find(in, udta.get().contentStart(), udta.get().end(), META);
        if (meta.isEmpty()) return;
        long metaContent = meta.get().contentStart() + META_VERSION_FLAGS_BYTES;
        Optional<Atom> ilst = find(in, metaContent, meta.get().end(), ILST);
        if (ilst.isEmpty()) return;
        readItems(in, ilst.get(), out);
    }

    private static void readItems(BoundedReader in, Atom ilst, TagAccumulator out) throws IOException {
        in.seek(ilst.contentStart());
        Atom item = next(in, ilst.end());
        while (item != null) {
            readItem(in, item, out);
            in.seek(item.end());
            item = next(in, ilst.end());
        }
    }

    private static void readItem(BoundedReader in, Atom item, TagAccumulator out) throws IOException {
        Optional<Atom> data = find(in, item.contentStart(), item.end(), DATA);
        if (data.isEmpty()) return;
        long payloadStart = data.get().contentStart() + DATA_PREFIX_BYTES;
        long payloadLength = data.get().end() - payloadStart;
        if (payloadLength < 0) return;
        in.seek(data.get().contentStart());
        int flags = (int) Bytes.u32be(in.read(DATA_PREFIX_BYTES, DATA_PREFIX_BYTES), 0) & FLAGS_MASK;
        if (item.type() == COVR) {
            readCover(in, payloadLength, flags, out);
        } else if (item.type() == TRKN) {
            readTrackNumber(in, payloadLength, out);
        } else if (payloadLength <= BoundedReader.MAX_FIELD_BYTES) {
            readText(in, item.type(), payloadLength, out);
        }
    }

    private static void readText(BoundedReader in, int type, long length, TagAccumulator out) throws IOException {
        if (type != TITLE && type != ARTIST && type != ALBUM && type != DAY) return;
        String text = new String(in.read(length, BoundedReader.MAX_FIELD_BYTES), StandardCharsets.UTF_8);
        if (type == TITLE) out.title(text);
        if (type == ARTIST) out.artist(text);
        if (type == ALBUM) out.album(text);
        if (type == DAY) out.year(text);
    }

    private static void readTrackNumber(BoundedReader in, long length, TagAccumulator out) throws IOException {
        if (length < TRACK_PAYLOAD_BYTES) return;
        out.track(Bytes.u16be(in.read(length, BoundedReader.MAX_FIELD_BYTES), TRACK_NUMBER_OFFSET));
    }

    private static void readCover(BoundedReader in, long length, int flags, TagAccumulator out) throws IOException {
        if (flags != JPEG_FLAG && flags != PNG_FLAG) return;
        if (!out.wantsPicture() || length > BoundedReader.MAX_PICTURE_BYTES) {
            out.markPicture();
            return;
        }
        out.offerPicture(TagAccumulator.FRONT_COVER, in.read(length, BoundedReader.MAX_PICTURE_BYTES));
    }

    private static Optional<Atom> find(BoundedReader in, long from, long to, int type) throws IOException {
        in.seek(from);
        Atom atom = next(in, to);
        while (atom != null) {
            if (atom.type() == type) return Optional.of(atom);
            in.seek(atom.end());
            atom = next(in, to);
        }
        return Optional.empty();
    }

    private static Atom next(BoundedReader in, long limit) throws IOException {
        long start = in.position();
        if (start + ATOM_HEADER_BYTES > limit) return null;
        byte[] header = in.read(ATOM_HEADER_BYTES, ATOM_HEADER_BYTES);
        long size = Bytes.u32be(header, 0);
        int type = (int) Bytes.u32be(header, 4);
        long headerBytes = ATOM_HEADER_BYTES;
        if (size == EXTENDED_SIZE_MARKER) {
            if (start + ATOM_HEADER_BYTES + EXTENDED_SIZE_BYTES > limit) return null;
            size = Bytes.u64be(in.read(EXTENDED_SIZE_BYTES, EXTENDED_SIZE_BYTES), 0);
            headerBytes += EXTENDED_SIZE_BYTES;
        } else if (size == 0) {
            size = limit - start;
        }
        if (size < headerBytes || size > limit - start) return null;
        return new Atom(type, start, headerBytes, size);
    }

    private static int fourCc(String name) {
        return (int) Bytes.u32be(name.getBytes(StandardCharsets.ISO_8859_1), 0);
    }
}
