package com.marianocarrizo.dynamusic.audio;

import com.marianocarrizo.dynamusic.audio.cache.AudioCache;
import com.marianocarrizo.dynamusic.audio.cache.CacheKey;
import com.marianocarrizo.dynamusic.audio.pcm.GainMeasurer;
import com.marianocarrizo.dynamusic.audio.pcm.PcmConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

public final class AudioPrep {

    static final double FALLBACK_GAIN = 0.8;

    private final AudioCache cache;
    private final RouteResolver resolver;
    private final PcmConverter javaSound;
    private final PcmConverter ffmpeg;
    private final GainMeasurer gains;
    private final BooleanSupplier ffmpegAvailable;
    private final Executor userExecutor;
    private final Map<CacheKey, CompletableFuture<PreparedAudio>> inFlight = new ConcurrentHashMap<>();

    public AudioPrep(AudioCache cache, RouteResolver resolver, PcmConverter javaSound, PcmConverter ffmpeg,
                     GainMeasurer gains, BooleanSupplier ffmpegAvailable, Executor userExecutor) {
        this.cache = cache;
        this.resolver = resolver;
        this.javaSound = javaSound;
        this.ffmpeg = ffmpeg;
        this.gains = gains;
        this.ffmpegAvailable = ffmpegAvailable;
        this.userExecutor = userExecutor;
    }

    public CompletableFuture<PreparedAudio> prepare(File file) {
        return prepare(file, userExecutor);
    }

    public CompletableFuture<PreparedAudio> prepare(File file, Executor executor) {
        CacheKey key = CacheKey.of(file);
        CompletableFuture<PreparedAudio> created = new CompletableFuture<>();
        CompletableFuture<PreparedAudio> running = inFlight.putIfAbsent(key, created);
        if (running != null) return running;
        try {
            executor.execute(() -> finish(key, created, file));
        } catch (RuntimeException rejected) {
            inFlight.remove(key, created);
            created.completeExceptionally(rejected);
        }
        return created;
    }

    private void finish(CacheKey key, CompletableFuture<PreparedAudio> future, File file) {
        PreparedAudio result = null;
        Throwable failure = null;
        try {
            result = run(file, key);
        } catch (RuntimeException | Error problem) {
            failure = problem;
        }
        inFlight.remove(key, future);
        if (failure != null) {
            future.completeExceptionally(failure);
        } else {
            future.complete(result);
        }
    }

    private PreparedAudio run(File file, CacheKey key) {
        Optional<AudioCache.CachedAudio> hit = cache.lookup(key);
        if (hit.isPresent()) {
            return new PreparedAudio(hit.get().wav().map(Path::toFile).orElse(file), hit.get().gain());
        }
        PlaybackRoute route = resolver.resolve(file)
                .orElseThrow(() -> new UnplayableAudioException("Unsupported file: " + file.getName()));
        return switch (route) {
            case NATIVE -> prepareNative(file, key);
            case JAVA_SOUND -> convertWithJavaSound(file, key);
            case FFMPEG -> convertWithFfmpeg(file, key);
        };
    }

    private PreparedAudio prepareNative(File file, CacheKey key) {
        try {
            OptionalDouble measured = gains.ofNative(file);
            if (measured.isEmpty()) return new PreparedAudio(file, FALLBACK_GAIN);
            cache.storeGain(key, measured.getAsDouble());
            return new PreparedAudio(file, measured.getAsDouble());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UnplayableAudioException("Interrupted while measuring " + file.getName(), interrupted);
        }
    }

    private PreparedAudio convertWithJavaSound(File file, CacheKey key) {
        try {
            return convert(javaSound, file, key);
        } catch (IOException | UnplayableAudioException decodeFailure) {
            if (!ffmpegAvailable.getAsBoolean()) throw unplayable(file, decodeFailure);
            return convertWithFfmpeg(file, key);
        } catch (InterruptedException interrupted) {
            throw interrupted(file, interrupted);
        }
    }

    private PreparedAudio convertWithFfmpeg(File file, CacheKey key) {
        if (!ffmpegAvailable.getAsBoolean()) {
            String format = AudioFormat.fromFile(file).map(Enum::name).orElse("This format");
            throw new UnplayableAudioException(format + " files need ffmpeg, which was not found.");
        }
        try {
            return convert(ffmpeg, file, key);
        } catch (IOException failure) {
            throw unplayable(file, failure);
        } catch (InterruptedException interrupted) {
            throw interrupted(file, interrupted);
        }
    }

    private PreparedAudio convert(PcmConverter converter, File file, CacheKey key)
            throws IOException, InterruptedException {
        Path temporary = cache.temporaryFile(key);
        try {
            converter.convert(file, temporary);
            double gain = gains.ofWav(temporary.toFile());
            Path wav = cache.storeConverted(key, temporary, gain);
            return new PreparedAudio(wav.toFile(), gain);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    private static UnplayableAudioException unplayable(File file, Exception cause) {
        return new UnplayableAudioException("Could not convert " + file.getName(), cause);
    }

    private static UnplayableAudioException interrupted(File file, InterruptedException cause) {
        Thread.currentThread().interrupt();
        return new UnplayableAudioException("Interrupted while converting " + file.getName(), cause);
    }
}
