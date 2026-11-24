package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.audio.AudioPrep;
import com.marianocarrizo.dynamusic.audio.PcmProbe;
import com.marianocarrizo.dynamusic.audio.PreparedAudio;
import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class PlaybackRouter {

    private static final int OPEN_THREADS = 2;
    private static final String OPEN_THREAD_NAME = "stream-open";

    private final BooleanSupplier streamingEnabled;
    private final Function<File, CompletableFuture<PreparedAudio>> prepare;
    private final StreamOpener opener;
    private final Executor background;
    private final Executor fx;
    private final OutputArbiter arbiter;

    public PlaybackRouter(BooleanSupplier streamingEnabled, Function<File, CompletableFuture<PreparedAudio>> prepare,
            StreamOpener opener, Executor background, Executor fx, OutputArbiter arbiter) {
        this.arbiter = arbiter;
        this.streamingEnabled = streamingEnabled;
        this.prepare = prepare;
        this.opener = opener;
        this.background = background;
        this.fx = fx;
    }

    public static PlaybackRouter standard(AudioPrep prep, BooleanSupplier streamingEnabled,
            BooleanSupplier exclusiveEnabled, Executor fx) {
        OutputArbiter arbiter = OutputArbiter.process();
        Executor background = Executors.newFixedThreadPool(OPEN_THREADS, runnable -> {
            Thread thread = new Thread(runnable, OPEN_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        return new PlaybackRouter(streamingEnabled, prep::prepare,
                LineStreamOpener.standard(exclusiveEnabled, arbiter),
                background, fx, arbiter);
    }

    public boolean streams(File file) {
        return choose(streamingEnabled.getAsBoolean(), PlaybackUse.LISTEN, file) == EngineChoice.STREAMING;
    }

    public CompletableFuture<Boolean> streamsAsync(File file) {
        if (!EngineChoice.couldStream(streamingEnabled.getAsBoolean(), file)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> streams(file), background);
    }

    public CompletableFuture<PlaybackPlan> plan(File file, PlaybackUse use) {
        boolean enabled = streamingEnabled.getAsBoolean();
        if (!use.streamable() || !EngineChoice.couldStream(enabled, file)) return javaFx(file);
        return CompletableFuture.supplyAsync(() -> streaming(file, use, enabled), background)
                .thenCompose(plan -> plan.isPresent() ? CompletableFuture.completedFuture(plan.get()) : javaFx(file));
    }

    private Optional<PlaybackPlan> streaming(File file, PlaybackUse use, boolean enabled) {
        try {
            if (choose(enabled, use, file) != EngineChoice.STREAMING) return Optional.empty();
            return opener.open(file).map(stream -> new StreamingPlan(stream, fx));
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    private static EngineChoice choose(boolean enabled, PlaybackUse use, File file) {
        try {
            Optional<AudioFormat> format = AudioFormat.fromFile(file);
            return EngineChoice.of(enabled, use, file, () -> format.flatMap(known -> PcmProbe.read(file, known)));
        } catch (RuntimeException unreadable) {
            return EngineChoice.JAVAFX;
        }
    }

    private CompletableFuture<PlaybackPlan> javaFx(File file) {
        return prepare.apply(file).<PlaybackPlan>thenApply(prepared -> new JavaFxPlan(prepared, arbiter));
    }
}
