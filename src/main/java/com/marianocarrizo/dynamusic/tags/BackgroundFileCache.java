package com.marianocarrizo.dynamusic.tags;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class BackgroundFileCache<V> {

    static final int CAPACITY = 4096;

    private record Cached<T>(long size, long modified, T value) {
    }

    private final Function<File, V> loader;
    private final Executor worker;
    private final Executor notifier;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Cached<V>> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached<V>> eldest) {
            return size() > CAPACITY;
        }
    };

    public BackgroundFileCache(Function<File, V> loader, Executor worker, Executor notifier) {
        this.loader = loader;
        this.worker = worker;
        this.notifier = notifier;
    }

    public static <T> BackgroundFileCache<T> onBackgroundThread(String threadName, Function<File, T> loader,
                                                                Executor notifier) {
        return new BackgroundFileCache<>(loader, backgroundWorker(threadName), notifier);
    }

    public Optional<V> peek(File file) {
        synchronized (entries) {
            return Optional.ofNullable(entries.get(keyOf(file))).map(Cached::value);
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
            Cached<V> known = entries.get(keyOf(file));
            if (known != null && known.size() == size && known.modified() == modified) return false;
        }
        V value = loader.apply(file);
        synchronized (entries) {
            entries.put(keyOf(file), new Cached<>(size, modified, value));
        }
        return true;
    }

    private static String keyOf(File file) {
        return file.getAbsolutePath();
    }

    private static Executor backgroundWorker(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }
}
