package com.marianocarrizo.dynamusic.audio;

import com.marianocarrizo.dynamusic.service.FfmpegLocator;

import java.io.File;

public final class PlaybackSupport {

    public static final String FFMPEG_MISSING_MESSAGE = "This format needs ffmpeg, which was not found.";

    private PlaybackSupport() {}

    public static boolean isPlayable(File file) {
        return isPlayable(file, FfmpegLocator.isAvailable());
    }

    static boolean isPlayable(File file, boolean ffmpegAvailable) {
        return AudioFormat.fromFile(file)
                .map(format -> format.route() != PlaybackRoute.FFMPEG || ffmpegAvailable)
                .orElse(false);
    }

    public static boolean isBlockedByMissingFfmpeg(File file) {
        return AudioFormat.isSupported(file) && !isPlayable(file);
    }
}
