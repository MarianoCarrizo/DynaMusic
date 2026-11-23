package com.marianocarrizo.dynamusic.audio;

import com.marianocarrizo.dynamusic.tags.TagFormat;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum AudioFormat {
    MP3(PlaybackRoute.NATIVE, TagFormat.ID3V2, "mp3"),
    M4A(PlaybackRoute.NATIVE, TagFormat.MP4, "m4a"),
    AAC(PlaybackRoute.FFMPEG, TagFormat.NONE, "aac"),
    WAV(PlaybackRoute.NATIVE, TagFormat.NONE, "wav"),
    AIFF(PlaybackRoute.NATIVE, TagFormat.NONE, "aiff", "aif"),
    FLAC(PlaybackRoute.JAVA_SOUND, TagFormat.FLAC, "flac"),
    OGG(PlaybackRoute.FFMPEG, TagFormat.OGG, "ogg"),
    OPUS(PlaybackRoute.FFMPEG, TagFormat.OGG, "opus"),
    WMA(PlaybackRoute.FFMPEG, TagFormat.NONE, "wma"),
    APE(PlaybackRoute.FFMPEG, TagFormat.NONE, "ape");

    private static final String EXTENSION_SEPARATOR = ".";
    private static final String CHOOSER_PATTERN_PREFIX = "*.";
    private static final String DISPLAY_SEPARATOR = ", ";

    private final PlaybackRoute route;
    private final TagFormat tagFormat;
    private final List<String> extensions;

    AudioFormat(PlaybackRoute route, TagFormat tagFormat, String... extensions) {
        this.route = route;
        this.tagFormat = tagFormat;
        this.extensions = List.of(extensions);
    }

    public PlaybackRoute route() {
        return route;
    }

    public TagFormat tagFormat() {
        return tagFormat;
    }

    public boolean matches(File file) {
        return fromFile(file).filter(format -> format == this).isPresent();
    }

    public String primaryExtension() {
        return extensions.get(0);
    }

    public List<String> extensions() {
        return extensions;
    }

    public static Optional<AudioFormat> fromExtension(String extension) {
        if (extension == null) return Optional.empty();
        String normalized = stripLeadingDot(extension).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(format -> format.extensions.contains(normalized)).findFirst();
    }

    public static Optional<AudioFormat> fromFileName(String fileName) {
        if (fileName == null) return Optional.empty();
        int dot = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        return dot < 0 ? Optional.empty() : fromExtension(fileName.substring(dot + 1));
    }

    public static Optional<AudioFormat> fromFile(File file) {
        return file == null ? Optional.empty() : fromFileName(file.getName());
    }

    public static boolean isSupported(File file) {
        return fromFile(file).isPresent();
    }

    public static List<String> allExtensions() {
        return Arrays.stream(values()).flatMap(format -> format.extensions.stream()).toList();
    }

    public static List<String> chooserPatterns() {
        return allExtensions().stream().map(extension -> CHOOSER_PATTERN_PREFIX + extension).toList();
    }

    public static String displayList() {
        return String.join(DISPLAY_SEPARATOR, allExtensions());
    }

    private static String stripLeadingDot(String extension) {
        return extension.startsWith(EXTENSION_SEPARATOR) ? extension.substring(1) : extension;
    }
}
