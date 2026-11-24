package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.audio.AudioPrep;
import com.marianocarrizo.dynamusic.audio.PcmProbe;
import com.marianocarrizo.dynamusic.audio.PreparedAudio;
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

    public PlaybackRouter(BooleanSupplier streamingEnabled, Function<File, CompletableFuture<PreparedAudio>> prepare,
            StreamOpener opener, Executor background, Executor fx) {
        this.streamingEnabled = streamingEnabled;
        this.prepare = prepare;
        this.opener = opener;
        this.background = background;
        this.fx = fx;
    }

    public static PlaybackRouter standard(AudioPrep prep, BooleanSupplier streamingEnabled, Executor fx) {
        Executor background = Executors.newFixedThreadPool(OPEN_THREADS, runnable -> {
            Thread thread = new Thread(runnable, OPEN_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        return new PlaybackRouter(streamingEnabled, prep::prepare, LineStreamOpener.standard(), background, fx);
    }

    public boolean streams(File file) {
        return EngineChoice.couldStream(streamingEnabled.getAsBoolean(), file);
    }

    public CompletableFuture<PlaybackPlan> plan(File file, PlaybackUse use) {
        boolean enabled = streamingEnabled.getAsBoolean();
        if (!use.streamable() || !EngineChoice.couldStream(enabled, file)) return javaFx(file);
        return CompletableFuture.supplyAsync(() -> streaming(file, use, enabled), background)
                .thenCompose(plan -> plan.isPresent() ? CompletableFuture.completedFuture(plan.get()) : javaFx(file));
    }

    private Optional<PlaybackPlan> streaming(File file, PlaybackUse use, boolean enabled) {
        try {
            Optional<AudioFormat> format = AudioFormat.fromFile(file);
            EngineChoice choice = EngineChoice.of(enabled, use, file,
                    () -> format.flatMap(known -> PcmProbe.read(file, known)));
            if (choice != EngineChoice.STREAMING) return Optional.empty();
            return opener.open(file).map(stream -> new StreamingPlan(stream, fx));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private CompletableFuture<PlaybackPlan> javaFx(File file) {
        return prepare.apply(file).<PlaybackPlan>thenApply(JavaFxPlan::new);
    }
}
