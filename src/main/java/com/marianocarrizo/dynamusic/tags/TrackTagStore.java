package com.marianocarrizo.dynamusic.tags;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class TrackTagStore {

    static final int CAPACITY = BackgroundFileCache.CAPACITY;
    private static final String WORKER_NAME = "track-tags";

    private final BackgroundFileCache<TrackTags> cache;

    public TrackTagStore(Function<File, TagFormat> formatOf, Executor notifier) {
        this.cache = BackgroundFileCache.onBackgroundThread(WORKER_NAME, loaderFor(formatOf), notifier);
    }

    TrackTagStore(Function<File, TagFormat> formatOf, Executor worker, Executor notifier) {
        this.cache = new BackgroundFileCache<>(loaderFor(formatOf), worker, notifier);
    }

    public Optional<TrackTags> peek(File file) {
        return cache.peek(file);
    }

    public void request(File file) {
        cache.request(file);
    }

    public void request(Collection<File> files) {
        cache.request(files);
    }

    public void onLoaded(Runnable listener) {
        cache.onLoaded(listener);
    }

    private static Function<File, TrackTags> loaderFor(Function<File, TagFormat> formatOf) {
        return file -> formatOf.apply(file).readTags(file);
    }
}
