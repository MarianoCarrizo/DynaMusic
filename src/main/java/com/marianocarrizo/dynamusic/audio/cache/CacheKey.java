package com.marianocarrizo.dynamusic.audio.cache;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public record CacheKey(String hex) {

    static final int PIPELINE_VERSION = 1;
    private static final String SEPARATOR = "|";

    public static CacheKey of(File file) {
        return of(file, file.length(), file.lastModified());
    }

    public static CacheKey of(File file, long size, long modifiedMillis) {
        String normalized = file.getAbsoluteFile().toPath().normalize().toString().toLowerCase(Locale.ROOT);
        String identity = normalized + SEPARATOR + size + SEPARATOR + modifiedMillis + SEPARATOR + PIPELINE_VERSION;
        return new CacheKey(sha256Hex(identity));
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
