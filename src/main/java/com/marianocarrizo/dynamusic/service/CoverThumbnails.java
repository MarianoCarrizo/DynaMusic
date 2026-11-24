package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.tags.CoverReader;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CoverThumbnails {

    public static final int PIXELS = 150;
    static final int CACHE_SIZE = 240;
    private static final int WORKERS = 2;
    private static final String PLACEHOLDER_RESOURCE = "/images/cover-placeholder.png";

    private record Waiter(BooleanSupplier wanted, Consumer<Optional<Image>> sink) {
    }

    private final LruCache<String, Optional<Image>> cache = new LruCache<>(CACHE_SIZE);
    private final Map<String, List<Waiter>> pending = new HashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(WORKERS, runnable -> {
        Thread thread = new Thread(runnable, "cover-thumbs");
        thread.setDaemon(true);
        return thread;
    });
    private Image placeholder;

    public Image placeholder() {
        if (placeholder == null) {
            placeholder = new Image(Objects.requireNonNull(getClass().getResource(PLACEHOLDER_RESOURCE)).toExternalForm(),
                    PIXELS, PIXELS, true, true);
        }
        return placeholder;
    }

    public void load(String key, File coverTrack, File folder, BooleanSupplier wanted,
                     Consumer<Optional<Image>> onResult) {
        Optional<Image> known = cache.get(key);
        if (known != null) {
            onResult.accept(known);
            return;
        }
        Waiter waiter = new Waiter(wanted, onResult);
        List<Waiter> waiting = pending.get(key);
        if (waiting != null) {
            waiting.add(waiter);
            return;
        }
        List<Waiter> created = new CopyOnWriteArrayList<>();
        created.add(waiter);
        pending.put(key, created);
        workers.execute(() -> decode(key, coverTrack, folder, created));
    }

    private void decode(String key, File coverTrack, File folder, List<Waiter> waiters) {
        boolean skipped = waiters.stream().noneMatch(waiter -> waiter.wanted().getAsBoolean());
        Optional<Image> image = skipped ? Optional.empty() : read(coverTrack, folder);
        Platform.runLater(() -> finish(key, coverTrack, folder, skipped, image));
    }

    private void finish(String key, File coverTrack, File folder, boolean skipped, Optional<Image> image) {
        List<Waiter> waiters = pending.remove(key);
        if (!skipped) cache.put(key, image);
        for (Waiter waiter : waiters) {
            if (!waiter.wanted().getAsBoolean()) continue;
            if (skipped) load(key, coverTrack, folder, waiter.wanted(), waiter.sink());
            else waiter.sink().accept(image);
        }
    }

    private static Optional<Image> read(File coverTrack, File folder) {
        Optional<Image> embedded = coverTrack == null ? Optional.empty()
                : CoverReader.read(coverTrack, AudioFormat.tagFormatOf(coverTrack))
                        .map(CoverThumbnails::decode).filter(image -> !image.isError());
        if (embedded.isPresent()) return embedded;
        return CoverFiles.find(folder)
                .map(file -> new Image(file.toURI().toString(), PIXELS, PIXELS, true, true, false))
                .filter(image -> !image.isError());
    }

    private static Image decode(byte[] bytes) {
        return new Image(new ByteArrayInputStream(bytes), PIXELS, PIXELS, true, true);
    }
}
