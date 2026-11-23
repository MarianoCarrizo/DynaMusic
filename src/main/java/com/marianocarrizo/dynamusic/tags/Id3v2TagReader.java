package com.marianocarrizo.dynamusic.tags;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class Id3v2TagReader implements TagReader {

    private static final String MAGIC = "ID3";
    private static final int TAG_HEADER_BYTES = 10;
    private static final int FRAME_HEADER_BYTES = 10;
    private static final int VERSION_OFFSET = 3;
    private static final int FLAGS_OFFSET = 5;
    private static final int SIZE_OFFSET = 6;
    private static final int VERSION_2_3 = 3;
    private static final int VERSION_2_4 = 4;
    private static final int UNSYNCHRONISATION_FLAG = 0x80;
    private static final int EXTENDED_HEADER_FLAG = 0x40;
    private static final int ENCODING_UTF16_WITH_BOM = 1;
    private static final int ENCODING_UTF16_BE = 2;
    private static final int ENCODING_UTF8 = 3;
    private static final int PICTURE_OVERHEAD_BYTES = 64 * 1024;
    private static final char VALUE_SEPARATOR = '\0';

    @Override
    public void read(BoundedReader in, TagAccumulator out) throws IOException {
        byte[] header = in.read(TAG_HEADER_BYTES, TAG_HEADER_BYTES);
        int version = header[VERSION_OFFSET];
        boolean supported = Bytes.startsWith(header, 0, MAGIC) && (version == VERSION_2_3 || version == VERSION_2_4)
                && Bytes.isSynchsafe(header, SIZE_OFFSET);
        if (!supported) return;
        int flags = header[FLAGS_OFFSET] & 0xFF;
        long declared = Bytes.synchsafe32(header, SIZE_OFFSET);
        long size = Math.min(declared, in.remaining());
        if ((flags & UNSYNCHRONISATION_FLAG) != 0) {
            byte[] body = removeUnsynchronisation(in.read(size, BoundedReader.MAX_METADATA_BYTES));
            BoundedReader unsynchronised = new BoundedReader(ByteSource.ofBytes(body));
            readFrames(unsynchronised, version, flags, unsynchronised.length(), out);
        } else {
            readFrames(in, version, flags, in.position() + size, out);
        }
    }

    private static void readFrames(BoundedReader in, int version, int flags, long end, TagAccumulator out)
            throws IOException {
        if ((flags & EXTENDED_HEADER_FLAG) != 0) skipExtendedHeader(in, version);
        while (in.position() + FRAME_HEADER_BYTES <= end) {
            byte[] header = in.read(FRAME_HEADER_BYTES, FRAME_HEADER_BYTES);
            if (header[0] == 0) return;
            String id = frameId(header);
            if (id == null) return;
            boolean synchsafeSize = version == VERSION_2_4;
            if (synchsafeSize && !Bytes.isSynchsafe(header, 4)) return;
            long size = synchsafeSize ? Bytes.synchsafe32(header, 4) : Bytes.u32be(header, 4);
            if (in.position() + size > end) return;
            readFrame(in, id, size, out);
        }
    }

    private static void skipExtendedHeader(BoundedReader in, int version) throws IOException {
        byte[] size = in.read(4, 4);
        long length = version == VERSION_2_4 ? Bytes.synchsafe32(size, 0) - 4 : Bytes.u32be(size, 0);
        in.skip(length);
    }

    private static String frameId(byte[] header) {
        for (int i = 0; i < 4; i++) {
            char character = (char) header[i];
            if (!(character >= 'A' && character <= 'Z') && !(character >= '0' && character <= '9')) return null;
        }
        return new String(header, 0, 4, StandardCharsets.US_ASCII);
    }

    private static void readFrame(BoundedReader in, String id, long size, TagAccumulator out) throws IOException {
        if (id.equals("APIC")) {
            readPicture(in, size, out);
            return;
        }
        boolean text = id.equals("TIT2") || id.equals("TPE1") || id.equals("TALB") || id.equals("TRCK")
                || id.equals("TYER") || id.equals("TDRC");
        if (!text || size > BoundedReader.MAX_FIELD_BYTES || size < 1) {
            in.skip(size);
            return;
        }
        String[] values = decodeText(in.read(size, BoundedReader.MAX_FIELD_BYTES)).split(String.valueOf(VALUE_SEPARATOR));
        for (String value : values) assign(id, value, out);
    }

    private static void assign(String id, String value, TagAccumulator out) {
        switch (id) {
            case "TIT2" -> out.title(value);
            case "TPE1" -> out.artist(value);
            case "TALB" -> out.album(value);
            case "TRCK" -> out.track(value);
            default -> out.year(value);
        }
    }

    private static void readPicture(BoundedReader in, long size, TagAccumulator out) throws IOException {
        long limit = BoundedReader.MAX_PICTURE_BYTES + PICTURE_OVERHEAD_BYTES;
        if (!out.wantsPicture() || size > limit) {
            out.markPicture();
            in.skip(size);
            return;
        }
        parsePicture(in.read(size, limit), out);
    }

    private static void parsePicture(byte[] frame, TagAccumulator out) {
        if (frame.length < 4) return;
        int encoding = frame[0] & 0xFF;
        int position = indexOfTerminator(frame, 1, 1);
        if (position < 0 || position + 2 > frame.length) return;
        position++;
        int type = frame[position++] & 0xFF;
        int terminatorWidth = encoding == ENCODING_UTF16_WITH_BOM || encoding == ENCODING_UTF16_BE ? 2 : 1;
        int descriptionEnd = indexOfTerminator(frame, position, terminatorWidth);
        if (descriptionEnd < 0) return;
        int dataStart = descriptionEnd + terminatorWidth;
        if (dataStart >= frame.length) return;
        byte[] data = new byte[frame.length - dataStart];
        System.arraycopy(frame, dataStart, data, 0, data.length);
        out.offerPicture(type, data);
    }

    private static int indexOfTerminator(byte[] bytes, int from, int width) {
        for (int i = from; i + width <= bytes.length; i += width) {
            if (width == 1 && bytes[i] == 0) return i;
            if (width == 2 && bytes[i] == 0 && bytes[i + 1] == 0) return i;
        }
        return -1;
    }

    private static String decodeText(byte[] frame) {
        int encoding = frame[0] & 0xFF;
        Charset charset = switch (encoding) {
            case ENCODING_UTF16_WITH_BOM -> StandardCharsets.UTF_16;
            case ENCODING_UTF16_BE -> StandardCharsets.UTF_16BE;
            case ENCODING_UTF8 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        return new String(frame, 1, frame.length - 1, charset);
    }

    private static byte[] removeUnsynchronisation(byte[] data) {
        ByteArrayOutputStream result = new ByteArrayOutputStream(data.length);
        for (int i = 0; i < data.length; i++) {
            result.write(data[i]);
            boolean padding = data[i] == (byte) 0xFF && i + 1 < data.length && data[i + 1] == 0;
            if (padding) i++;
        }
        return result.toByteArray();
    }
}
