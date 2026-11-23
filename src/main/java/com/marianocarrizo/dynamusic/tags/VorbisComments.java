package com.marianocarrizo.dynamusic.tags;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

final class VorbisComments {

    private static final String PICTURE_KEY = "METADATA_BLOCK_PICTURE";
    private static final String PICTURE_PREFIX = PICTURE_KEY + "=";
    private static final int LENGTH_BYTES = 4;
    private static final long MAX_PICTURE_FIELD_BYTES = 2L * BoundedReader.MAX_PICTURE_BYTES;

    private VorbisComments() {}

    static void parse(byte[] block, TagAccumulator out) {
        if (block.length < LENGTH_BYTES) return;
        long offset = LENGTH_BYTES + Bytes.u32le(block, 0);
        if (offset + LENGTH_BYTES > block.length) return;
        long count = Bytes.u32le(block, (int) offset);
        offset += LENGTH_BYTES;
        for (long i = 0; i < count && offset + LENGTH_BYTES <= block.length; i++) {
            long length = Bytes.u32le(block, (int) offset);
            offset += LENGTH_BYTES;
            if (length > block.length - offset) return;
            readField(block, (int) offset, (int) length, out);
            offset += length;
        }
    }

    private static void readField(byte[] block, int offset, int length, TagAccumulator out) {
        if (length > BoundedReader.MAX_FIELD_BYTES) {
            if (length <= MAX_PICTURE_FIELD_BYTES && startsWithPictureKey(block, offset, length)) {
                readPictureValue(valueAfterPrefix(block, offset, length), out);
            }
            return;
        }
        String field = new String(block, offset, length, StandardCharsets.UTF_8);
        int separator = field.indexOf('=');
        if (separator <= 0) return;
        String key = field.substring(0, separator).toUpperCase(Locale.ROOT);
        String value = field.substring(separator + 1);
        switch (key) {
            case "TITLE" -> out.title(value);
            case "ARTIST" -> out.artist(value);
            case "ALBUM" -> out.album(value);
            case "TRACKNUMBER" -> out.track(value);
            case "DATE", "YEAR" -> out.year(value);
            case PICTURE_KEY -> readPictureValue(value, out);
            default -> {
            }
        }
    }

    private static boolean startsWithPictureKey(byte[] block, int offset, int length) {
        if (length < PICTURE_PREFIX.length()) return false;
        String prefix = new String(block, offset, PICTURE_PREFIX.length(), StandardCharsets.US_ASCII);
        return prefix.equalsIgnoreCase(PICTURE_PREFIX);
    }

    private static String valueAfterPrefix(byte[] block, int offset, int length) {
        return new String(block, offset + PICTURE_PREFIX.length(), length - PICTURE_PREFIX.length(),
                StandardCharsets.US_ASCII);
    }

    private static void readPictureValue(String base64, TagAccumulator out) {
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(base64);
            PictureBlock.parse(decoded).ifPresent(picture -> out.offerPicture(picture.type(), picture.data()));
        } catch (IllegalArgumentException invalidBase64) {
            return;
        }
    }
}
