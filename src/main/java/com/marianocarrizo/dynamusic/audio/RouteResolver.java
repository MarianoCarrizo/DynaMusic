package com.marianocarrizo.dynamusic.audio;

import java.io.File;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class RouteResolver {

    private final BooleanSupplier ffmpegAvailable;

    public RouteResolver(BooleanSupplier ffmpegAvailable) {
        this.ffmpegAvailable = ffmpegAvailable;
    }

    public Optional<PlaybackRoute> resolve(File file) {
        return AudioFormat.fromFile(file).map(format -> resolve(file, format));
    }

    private PlaybackRoute resolve(File file, AudioFormat format) {
        return switch (format) {
            case M4A -> Mp4Probe.isAlac(file) ? PlaybackRoute.FFMPEG : PlaybackRoute.NATIVE;
            case WAV, AIFF, FLAC -> PcmProbe.read(file, format).map(info -> routeForPcm(format, info))
                    .orElse(format.route());
            default -> format.route();
        };
    }

    private PlaybackRoute routeForPcm(AudioFormat format, PcmProbe.Info info) {
        if (info.exceedsNativeRate() && ffmpegAvailable.getAsBoolean()) return PlaybackRoute.FFMPEG;
        if (info.exceedsNativeDepth()) return PlaybackRoute.JAVA_SOUND;
        return format.route();
    }
}
