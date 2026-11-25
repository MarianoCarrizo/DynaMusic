package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.service.AppDataFolder;
import com.marianocarrizo.dynamusic.tags.TrackTags;
import javafx.application.Platform;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class StandardLibraryIndex {

    private static final String CACHE_FOLDER = "cache";
    private static final String CACHE_FILE = "library-index.tsv";
    private static final String WORKER_NAME = "library-index";

    private StandardLibraryIndex() {}

    public static LibraryIndex create() {
        Path cache = AppDataFolder.localRoot().resolve(CACHE_FOLDER).resolve(CACHE_FILE);
        return new LibraryIndex(StandardLibraryIndex::readTags, new FileSystemWalker(), new IndexCacheFile(cache),
                worker(), Platform::runLater);
    }

    private static TrackTags readTags(Path path) {
        File file = path.toFile();
        return AudioFormat.tagFormatOf(file).readTags(file);
    }

    private static Executor worker() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, WORKER_NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }
}
