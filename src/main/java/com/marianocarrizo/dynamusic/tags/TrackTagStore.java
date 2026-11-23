package com.marianocarrizo.dynamusic.tags;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class TrackTagStore {

    static final int CAPACITY = 4096;
    private static final String WORKER_NAME = "track-tags";

    private record CachedTags(long size, long modified, TrackTags tags) {
    }

    private final Function<File, TagFormat> formatOf;
    private final Executor worker;
    private final Executor notifier;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, CachedTags> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedTags> eldest) {
            return size() > CAPACITY;
        }
    };

    public TrackTagStore(Function<File, TagFormat> formatOf, Executor notifier) {
        this(formatOf, backgroundWorker(), notifier);
    }

    TrackTagStore(Function<File, TagFormat> formatOf, Executor worker, Executor notifier) {
        this.formatOf = formatOf;
        this.worker = worker;
        this.notifier = notifier;
    }

    public Optional<TrackTags> peek(File file) {
        synchronized (entries) {
            return Optional.ofNullable(entries.get(keyOf(file))).map(CachedTags::tags);
        }
    }

    public void request(File file) {
        request(List.of(file));
    }

    public void request(Collection<File> files) {
        List<File> batch = new ArrayList<>();
        for (File file : files) {
            if (pending.add(keyOf(file))) batch.add(file);
        }
        if (!batch.isEmpty()) worker.execute(() -> load(batch));
    }

    public void onLoaded(Runnable listener) {
        listeners.add(listener);
    }

    private void load(List<File> batch) {
        boolean changed = false;
        for (File file : batch) {
            try {
                changed |= loadOne(file);
            } finally {
                pending.remove(keyOf(file));
            }
        }
        if (changed) notifier.execute(() -> listeners.forEach(Runnable::run));
    }

    private boolean loadOne(File file) {
        long size = file.length();
        long modified = file.lastModified();
        synchronized (entries) {
            CachedTags known = entries.get(keyOf(file));
            if (known != null && known.size() == size && known.modified() == modified) return false;
        }
        TrackTags tags = formatOf.apply(file).readTags(file);
        synchronized (entries) {
            entries.put(keyOf(file), new CachedTags(size, modified, tags));
        }
        return true;
    }

    private static String keyOf(File file) {
        return file.getAbsolutePath();
    }

    private static Executor backgroundWorker() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, WORKER_NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }
}
