package com.marianocarrizo.dynamusic.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record OpenRequest(List<File> files) {

    static final String HEADER = "DYNAMUSIC-OPEN/1";
    static final int MAX_BYTES = 16 * 1024;
    static final int MAX_FILES = 32;
    static final int MAX_PATH_LENGTH = 1024;
    private static final String LINE_SEPARATOR = "\n";

    public OpenRequest {
        files = List.copyOf(files);
    }

    public static OpenRequest fromPaths(Collection<String> rawPaths) {
        List<File> playable = new ArrayList<>();
        for (String raw : rawPaths) {
            if (playable.size() == MAX_FILES) break;
            playableFile(raw, false).ifPresent(playable::add);
        }
        return new OpenRequest(playable);
    }

    public static Optional<OpenRequest> decode(byte[] message) {
        if (message == null || message.length > MAX_BYTES) return Optional.empty();
        String[] lines = new String(message, StandardCharsets.UTF_8).split("\r?\n");
        if (lines.length == 0 || !HEADER.equals(lines[0])) return Optional.empty();
        List<File> playable = new ArrayList<>();
        for (int i = 1; i < lines.length && playable.size() < MAX_FILES; i++) {
            playableFile(lines[i], true).ifPresent(playable::add);
        }
        return Optional.of(new OpenRequest(playable));
    }

    public byte[] encode() {
        StringBuilder message = new StringBuilder(HEADER);
        for (File file : files) {
            message.append(LINE_SEPARATOR).append(file.getAbsolutePath());
        }
        return message.append(LINE_SEPARATOR).toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Optional<File> playableFile(String raw, boolean requireAbsolute) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_PATH_LENGTH || hasControlCharacter(raw)
                || isNetworkOrDevicePath(raw)) {
            return Optional.empty();
        }
        Path path;
        try {
            path = Paths.get(raw);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        if (requireAbsolute && !path.isAbsolute()) return Optional.empty();
        File file = path.toAbsolutePath().normalize().toFile();
        boolean playable = file.isFile() && AudioFileCollector.isAudioFile(file);
        return playable ? Optional.of(file) : Optional.empty();
    }

    static boolean isNetworkOrDevicePath(String raw) {
        return raw.length() >= 2 && isSeparator(raw.charAt(0)) && isSeparator(raw.charAt(1));
    }

    private static boolean isSeparator(char character) {
        return character == '\\' || character == '/';
    }

    private static boolean hasControlCharacter(String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }
}
