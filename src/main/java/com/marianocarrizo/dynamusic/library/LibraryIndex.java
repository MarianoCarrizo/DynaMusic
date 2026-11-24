package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LibraryIndex {

    private final TagSource tagSource;
    private final FileWalker walker;
    private final IndexStore store;
    private final Executor worker;
    private final Supplier<PublishGate> gates;
    private final List<Consumer<LibrarySnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<IndexProgress>> progressListeners = new CopyOnWriteArrayList<>();
    private final FxCoalescer<LibrarySnapshot> snapshots;
    private final FxCoalescer<IndexProgress> progress;
    private final Object lock = new Object();
    private volatile int generation;

    public LibraryIndex(TagSource tagSource, FileWalker walker, IndexStore store, Executor worker, Executor fx) {
        this(tagSource, walker, store, worker, fx, PublishGate::standard);
    }

    LibraryIndex(TagSource tagSource, FileWalker walker, IndexStore store, Executor worker, Executor fx,
                 Supplier<PublishGate> gates) {
        this.tagSource = tagSource;
        this.walker = walker;
        this.store = store;
        this.worker = worker;
        this.gates = gates;
        this.snapshots = new FxCoalescer<>(fx, value -> snapshotListeners.forEach(listener -> listener.accept(value)));
        this.progress = new FxCoalescer<>(fx, value -> progressListeners.forEach(listener -> listener.accept(value)));
    }

    public void onSnapshot(Consumer<LibrarySnapshot> listener) {
        snapshotListeners.add(listener);
    }

    public void onProgress(Consumer<IndexProgress> listener) {
        progressListeners.add(listener);
    }

    public void open(Path root) {
        int mine = supersede();
        worker.execute(() -> new Scan(root.toAbsolutePath().normalize(), mine).run());
    }

    public void cancel() {
        supersede();
    }

    private int supersede() {
        synchronized (lock) {
            snapshots.clear();
            progress.clear();
            return ++generation;
        }
    }

    private void publish(int owner, LibrarySnapshot snapshot) {
        synchronized (lock) {
            if (owner == generation) snapshots.publish(snapshot);
        }
    }

    private void publish(int owner, IndexProgress update) {
        synchronized (lock) {
            if (owner == generation) progress.publish(update);
        }
    }

    private final class Scan {

        private final Path root;
        private final int owner;
        private final PublishGate snapshotGate = gates.get();
        private final PublishGate progressGate = gates.get();
        private final Map<String, TrackEntry> current = new LinkedHashMap<>();
        private Map<String, TrackEntry> cached = Map.of();
        private int filesSeen;
        private int tagsRead;
        private int snapshotReads;

        Scan(Path root, int owner) {
            this.root = root;
            this.owner = owner;
        }

        void run() {
            try {
                cached = underRoot(store.load());
                publish(owner, LibraryGrouping.group(cached.values()));
                walker.walk(root, this::visit, this::superseded);
                finish();
            } catch (RuntimeException failed) {
                System.err.println("Library index failed: " + failed.getMessage());
                publish(owner, IndexProgress.IDLE);
            }
        }

        private void finish() {
            if (superseded()) {
                if (tagsRead > 0) store.save(merged().values());
                return;
            }
            if (tagsRead > 0 || current.size() != cached.size()) {
                publish(owner, LibraryGrouping.group(current.values()));
                store.save(current.values());
            }
            publish(owner, new IndexProgress(filesSeen, tagsRead, true));
        }

        private void visit(FileStat stat) {
            filesSeen++;
            String key = stat.path().toString();
            TrackEntry known = cached.get(key);
            boolean unchanged = known != null && known.size() == stat.size() && known.modified() == stat.modified();
            current.put(key, unchanged ? known : read(stat));
            if (progressGate.due(filesSeen)) publish(owner, new IndexProgress(filesSeen, tagsRead, false));
            if (tagsRead != snapshotReads && snapshotGate.due(tagsRead)) {
                snapshotReads = tagsRead;
                publish(owner, LibraryGrouping.group(merged().values()));
            }
        }

        private TrackEntry read(FileStat stat) {
            tagsRead++;
            TrackTags tags;
            try {
                tags = tagSource.read(stat.path());
            } catch (RuntimeException unreadable) {
                tags = TrackTags.EMPTY;
            }
            return new TrackEntry(stat.path().toString(), stat.size(), stat.modified(), tags);
        }

        private Map<String, TrackEntry> merged() {
            Map<String, TrackEntry> all = new HashMap<>(cached);
            all.putAll(current);
            return all;
        }

        private Map<String, TrackEntry> underRoot(Map<String, TrackEntry> all) {
            Map<String, TrackEntry> inside = new LinkedHashMap<>();
            for (TrackEntry entry : all.values()) {
                if (isUnder(entry.path())) inside.put(entry.path(), entry);
            }
            return inside;
        }

        private boolean isUnder(String path) {
            try {
                return Path.of(path).startsWith(root);
            } catch (RuntimeException invalid) {
                return false;
            }
        }

        private boolean superseded() {
            return owner != generation;
        }
    }
}
