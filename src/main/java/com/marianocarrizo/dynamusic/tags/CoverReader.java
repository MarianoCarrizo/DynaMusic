package com.marianocarrizo.dynamusic.tags;

import java.io.File;
import java.util.Optional;

public final class CoverReader {

    private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private CoverReader() {}

    public static Optional<byte[]> read(File track, TagFormat format) {
        return validated(format.readPicture(track));
    }

    static Optional<byte[]> validated(Optional<byte[]> picture) {
        return picture.filter(bytes -> bytes.length <= BoundedReader.MAX_PICTURE_BYTES)
                .filter(bytes -> startsWith(bytes, JPEG_SIGNATURE) || startsWith(bytes, PNG_SIGNATURE));
    }

    private static boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
