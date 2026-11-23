package com.marianocarrizo.dynamusic.tags;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class TagFixtures {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9};

    private TagFixtures() {}

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) joined.writeBytes(part);
        return joined.toByteArray();
    }

    static byte[] u32be(long value) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int) value).array();
    }

    static byte[] u32le(long value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) value).array();
    }

    static byte[] synchsafe(int value) {
        return new byte[] {(byte) ((value >> 21) & 0x7F), (byte) ((value >> 14) & 0x7F),
                (byte) ((value >> 7) & 0x7F), (byte) (value & 0x7F)};
    }

    static byte[] vorbisComment(String... fields) {
        byte[] vendor = "fixture".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        block.writeBytes(u32le(vendor.length));
        block.writeBytes(vendor);
        block.writeBytes(u32le(fields.length));
        for (String field : fields) {
            byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
            block.writeBytes(u32le(bytes.length));
            block.writeBytes(bytes);
        }
        return block.toByteArray();
    }

    static byte[] pictureBlock(int type, byte[] image) {
        byte[] mime = "image/jpeg".getBytes(StandardCharsets.US_ASCII);
        return concat(u32be(type), u32be(mime.length), mime, u32be(0), u32be(1), u32be(1), u32be(24), u32be(0),
                u32be(image.length), image);
    }

    static byte[] flacBlock(int type, boolean last, byte[] data) {
        byte[] header = {(byte) ((last ? 0x80 : 0) | type), (byte) (data.length >> 16), (byte) (data.length >> 8),
                (byte) data.length};
        return concat(header, data);
    }

    static byte[] flac(byte[]... blocks) {
        return concat("fLaC".getBytes(StandardCharsets.US_ASCII), concat(blocks));
    }

    static byte[] taggedFlac(String... fields) {
        byte[] streamInfo = flacBlock(0, false, new byte[34]);
        return flac(streamInfo, flacBlock(4, true, vorbisComment(fields)));
    }

    static byte[] id3Text(int version, String id, int encoding, String text) {
        Charset charset = switch (encoding) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        return id3Frame(version, id, concat(new byte[] {(byte) encoding}, text.getBytes(charset)));
    }

    static byte[] id3Apic(int version, int type, byte[] image) {
        byte[] data = concat(new byte[] {0}, "image/jpeg".getBytes(StandardCharsets.US_ASCII), new byte[] {0, (byte) type, 0},
                image);
        return id3Frame(version, "APIC", data);
    }

    static byte[] id3Frame(int version, String id, byte[] data) {
        byte[] size = version == 4 ? synchsafe(data.length) : u32be(data.length);
        return concat(id.getBytes(StandardCharsets.US_ASCII), size, new byte[] {0, 0}, data);
    }

    static byte[] id3Tag(int version, int flags, byte[]... frames) {
        byte[] body = concat(frames);
        return concat("ID3".getBytes(StandardCharsets.US_ASCII), new byte[] {(byte) version, 0, (byte) flags},
                synchsafe(body.length), body);
    }

    static byte[] oggPage(byte[] data, boolean continued, boolean partial) {
        int full = data.length / 255;
        int remainder = data.length % 255;
        int segments = partial ? full : full + 1;
        byte[] table = new byte[segments];
        for (int i = 0; i < segments; i++) table[i] = (byte) 255;
        if (!partial) table[segments - 1] = (byte) remainder;
        byte[] header = concat("OggS".getBytes(StandardCharsets.US_ASCII), new byte[] {0, (byte) (continued ? 1 : 0)},
                new byte[8], u32le(1), u32le(0), u32le(0), new byte[] {(byte) segments});
        return concat(header, table, data);
    }

    static byte[] vorbisIdentification() {
        return concat(new byte[] {1}, "vorbis".getBytes(StandardCharsets.US_ASCII), new byte[23]);
    }

    static byte[] vorbisCommentPacket(String... fields) {
        return concat(new byte[] {3}, "vorbis".getBytes(StandardCharsets.US_ASCII), vorbisComment(fields), new byte[] {1});
    }

    static byte[] opusHead() {
        return concat("OpusHead".getBytes(StandardCharsets.US_ASCII), new byte[11]);
    }

    static byte[] opusTagsPacket(String... fields) {
        return concat("OpusTags".getBytes(StandardCharsets.US_ASCII), vorbisComment(fields));
    }

    static byte[] ogg(byte[] identification, byte[] comment) {
        return concat(oggPage(identification, false, false), oggPage(comment, false, false));
    }

    static String pictureField(int type, byte[] image) {
        return "METADATA_BLOCK_PICTURE=" + Base64.getEncoder().encodeToString(pictureBlock(type, image));
    }

    static byte[] atom(String type, byte[]... children) {
        byte[] content = concat(children);
        return concat(u32be(8L + content.length), type.getBytes(StandardCharsets.ISO_8859_1), content);
    }

    static byte[] copyrightAtom(String suffix, byte[]... children) {
        byte[] content = concat(children);
        byte[] type = concat(new byte[] {(byte) 0xA9}, suffix.getBytes(StandardCharsets.ISO_8859_1));
        return concat(u32be(8L + content.length), type, content);
    }

    static byte[] dataAtom(int flags, byte[] payload) {
        return atom("data", u32be(flags), u32be(0), payload);
    }

    static byte[] mp4Meta(byte[]... items) {
        byte[] ilst = atom("ilst", items);
        return atom("moov", atom("udta", atom("meta", u32be(0), ilst)));
    }

    static byte[] mp4File(byte[] moov, boolean moovLast) {
        byte[] ftyp = atom("ftyp", "M4A ".getBytes(StandardCharsets.US_ASCII), new byte[4]);
        byte[] mdat = atom("mdat", new byte[2000]);
        return moovLast ? concat(ftyp, mdat, moov) : concat(ftyp, moov, mdat);
    }

    static byte[] textItem(String suffix, String text) {
        return copyrightAtom(suffix, dataAtom(1, text.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] trackItem(int track, int total) {
        byte[] payload = {0, 0, (byte) (track >> 8), (byte) track, (byte) (total >> 8), (byte) total, 0, 0};
        return atom("trkn", dataAtom(0, payload));
    }

    static byte[] coverItem(int flags, byte[] image) {
        return atom("covr", dataAtom(flags, image));
    }
}
