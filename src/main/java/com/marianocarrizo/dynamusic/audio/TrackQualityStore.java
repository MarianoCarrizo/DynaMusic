package com.marianocarrizo.dynamusic.audio;

import com.marianocarrizo.dynamusic.tags.BackgroundFileCache;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class TrackQualityStore {

    private static final String WORKER_NAME = "track-quality";

    private final BackgroundFileCache<Optional<AudioQuality>> cache;

    public TrackQualityStore(Executor notifier) {
        this.cache = BackgroundFileCache.onBackgroundThread(WORKER_NAME, AudioQualityReader::read, notifier);
    }

    public Optional<AudioQuality> peek(File file) {
        return cache.peek(file).flatMap(quality -> quality);
    }

    public void request(File file) {
        cache.request(file);
    }

    public void onLoaded(Runnable listener) {
        cache.onLoaded(listener);
    }
}
