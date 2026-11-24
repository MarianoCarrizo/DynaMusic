package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryIndexTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    private final List<FileStat> files = new ArrayList<>();
    private final List<String> reads = new ArrayList<>();
    private final Map<String, TrackEntry> stored = new LinkedHashMap<>();
    private final List<Integer> saves = new ArrayList<>();
    private final List<LibrarySnapshot> snapshots = new ArrayList<>();
    private final List<IndexProgress> progress = new ArrayList<>();
    private Runnable onRead = () -> { };
    private boolean failReads;
    private boolean walkSucceeds = true;

    private final TagSource tags = file -> {
        reads.add(file.toString());
        onRead.run();
        if (failReads) throw new IllegalStateException("unreadable");
        return new TrackTags("Song " + file.getFileName(), "Ana", "Album " + file.getParent().getFileName(), 1, "", false);
    };

    private final FileWalker walker = (root, sink, cancelled) -> {
        for (FileStat file : files) {
            if (cancelled.getAsBoolean()) return true;
            sink.accept(file);
        }
        return walkSucceeds;
    };

    private final IndexStore store = new IndexStore() {
        @Override
        public Map<String, TrackEntry> load() {
            return new LinkedHashMap<>(stored);
        }

        @Override
        public void save(Collection<TrackEntry> entries) {
            saves.add(entries.size());
            stored.clear();
            for (TrackEntry entry : entries) stored.put(entry.path(), entry);
        }
    };

    private LibraryIndex newIndex() {
        LibraryIndex index = new LibraryIndex(tags, walker, store, Runnable::run, Runnable::run,
                () -> new PublishGate(1, Long.MAX_VALUE, () -> 0));
        index.onSnapshot(snapshots::add);
        index.onProgress(progress::add);
        return index;
    }

    private static FileStat file(String relative, long size, long modified) {
        return new FileStat(ROOT.resolve(relative), size, modified);
    }

    private LibrarySnapshot lastSnapshot() {
        return snapshots.get(snapshots.size() - 1);
    }

    private IndexProgress lastProgress() {
        return progress.get(progress.size() - 1);
    }

    @Test
    void firstScanReadsEveryFileAndPublishesTheGroupedLibrary() {
        files.add(file("a/1.flac", 10, 1));
        files.add(file("b/1.flac", 10, 1));

        newIndex().open(ROOT);

        assertEquals(2, reads.size());
        assertEquals(2, lastSnapshot().trackCount());
        assertEquals(2, lastSnapshot().albums().size());
        assertEquals(new IndexProgress(2, 2, true), lastProgress());
    }

    @Test
    void firstSnapshotIsEmptyWhenNothingIsCached() {
        files.add(file("a/1.flac", 10, 1));

        newIndex().open(ROOT);

        assertEquals(0, snapshots.get(0).trackCount());
    }

    @Test
    void unchangedFilesAreNotReadAgain() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        reads.clear();
        saves.clear();

        newIndex().open(ROOT);

        assertTrue(reads.isEmpty());
        assertTrue(saves.isEmpty());
        assertEquals(1, lastSnapshot().trackCount());
        assertEquals(new IndexProgress(1, 0, true), lastProgress());
    }

    @Test
    void cachedLibraryIsPublishedBeforeTheWalkFinds() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        snapshots.clear();

        newIndex().open(ROOT);

        assertEquals(1, snapshots.get(0).trackCount());
    }

    @Test
    void fileWithANewModifiedTimeIsReadAgainAndUpdated() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        reads.clear();
        files.set(0, file("a/1.flac", 10, 2));

        newIndex().open(ROOT);

        assertEquals(1, reads.size());
        assertEquals(2, stored.get(files.get(0).path().toString()).modified());
    }

    @Test
    void fileWithANewSizeIsReadAgain() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        reads.clear();
        files.set(0, file("a/1.flac", 11, 1));

        newIndex().open(ROOT);

        assertEquals(1, reads.size());
    }

    @Test
    void newFileIsReadAndKnownOnesAreNot() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        reads.clear();
        files.add(file("a/2.flac", 10, 1));

        newIndex().open(ROOT);

        assertEquals(List.of(files.get(1).path().toString()), reads);
        assertEquals(2, lastSnapshot().trackCount());
    }

    @Test
    void removedFileDisappearsFromTheLibraryAndTheCache() {
        files.add(file("a/1.flac", 10, 1));
        files.add(file("a/2.flac", 10, 1));
        newIndex().open(ROOT);
        files.remove(1);

        newIndex().open(ROOT);

        assertEquals(1, lastSnapshot().trackCount());
        assertEquals(1, stored.size());
    }

    @Test
    void emptyCacheMeansAFullScanWithoutCrashing() {
        files.add(file("a/1.flac", 10, 1));
        files.add(file("a/2.flac", 10, 1));

        newIndex().open(ROOT);

        assertEquals(2, reads.size());
        assertEquals(List.of(2), saves);
    }

    @Test
    void cachedEntriesOutsideTheRootAreIgnored() {
        Path other = Path.of("/elsewhere/x.flac").toAbsolutePath();
        stored.put(other.toString(), new TrackEntry(other.toString(), 1, 1, TrackTags.EMPTY));

        newIndex().open(ROOT);

        assertEquals(0, snapshots.get(0).trackCount());
        assertEquals(0, lastSnapshot().trackCount());
    }

    @Test
    void cancellingMidScanStopsReadingAndKeepsPartialResultsValid() {
        for (int i = 0; i < 10; i++) files.add(file("a/" + i + ".flac", 10, 1));
        LibraryIndex index = newIndex();
        onRead = () -> {
            if (reads.size() == 3) index.cancel();
        };

        index.open(ROOT);

        assertEquals(3, reads.size());
        assertFalse(lastProgress().done());
        assertTrue(lastSnapshot().trackCount() <= 3);
        assertEquals(List.of(3), saves);
    }

    @Test
    void cancellingAfterOnlyCacheHitsSavesNothing() {
        files.add(file("a/1.flac", 10, 1));
        files.add(file("a/2.flac", 10, 1));
        newIndex().open(ROOT);
        saves.clear();
        LibraryIndex[] holder = new LibraryIndex[1];
        holder[0] = new LibraryIndex(tags, (root, sink, cancelled) -> {
            sink.accept(files.get(0));
            holder[0].cancel();
            return true;
        }, store, Runnable::run, Runnable::run, () -> new PublishGate(1, Long.MAX_VALUE, () -> 0));

        holder[0].open(ROOT);

        assertTrue(saves.isEmpty());
    }

    @Test
    void cancelledScanNeverPublishesDone() {
        for (int i = 0; i < 5; i++) files.add(file("a/" + i + ".flac", 10, 1));
        LibraryIndex index = newIndex();
        onRead = () -> {
            if (reads.size() == 2) index.cancel();
        };

        index.open(ROOT);

        assertTrue(progress.stream().noneMatch(IndexProgress::done));
    }

    @Test
    void progressGrowsWhileScanningAndEndsDone() {
        for (int i = 0; i < 3; i++) files.add(file("a/" + i + ".flac", 10, 1));

        newIndex().open(ROOT);

        List<Integer> seen = progress.stream().map(IndexProgress::filesSeen).toList();
        assertEquals(List.of(1, 2, 3, 3), seen);
        assertTrue(lastProgress().done());
        assertEquals(3, lastProgress().tagsRead());
    }

    @Test
    void partialSnapshotsAppearWhileNewFilesAreBeingRead() {
        for (int i = 0; i < 3; i++) files.add(file("a/" + i + ".flac", 10, 1));

        newIndex().open(ROOT);

        List<Integer> counts = snapshots.stream().map(LibrarySnapshot::trackCount).toList();
        assertEquals(0, counts.get(0));
        assertTrue(counts.contains(1));
        assertTrue(counts.contains(2));
        assertEquals(3, counts.get(counts.size() - 1));
    }

    @Test
    void unreadableTagsStillIndexTheFileAndDoNotCrash() {
        files.add(file("a/1.flac", 10, 1));
        failReads = true;

        newIndex().open(ROOT);

        assertEquals(1, lastSnapshot().trackCount());
        assertTrue(lastProgress().done());
    }

    @Test
    void walkerFailureStillEndsTheProgress() {
        LibraryIndex index = new LibraryIndex(tags, (root, sink, cancelled) -> {
            throw new IllegalStateException("walk failed");
        }, store, Runnable::run, Runnable::run, () -> new PublishGate(1, Long.MAX_VALUE, () -> 0));
        index.onProgress(progress::add);

        index.open(ROOT);

        assertTrue(lastProgress().done());
    }

    @Test
    void reopeningAnotherRootStartsFromThatRootsCache() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        snapshots.clear();
        files.clear();

        Path second = Path.of("/second").toAbsolutePath();
        newIndex().open(second);

        assertEquals(0, snapshots.get(0).trackCount());
        assertEquals(0, lastSnapshot().trackCount());
    }

    @Test
    void walkerReceivesTheCancelSignalOfTheCurrentGeneration() {
        List<BooleanSupplier> signals = new ArrayList<>();
        LibraryIndex index = new LibraryIndex(tags, (root, sink, cancelled) -> signals.add(cancelled), store,
                Runnable::run, Runnable::run, () -> new PublishGate(1, Long.MAX_VALUE, () -> 0));

        index.open(ROOT);
        assertFalse(signals.get(0).getAsBoolean());
        index.cancel();

        assertTrue(signals.get(0).getAsBoolean());
    }

    @Test
    void scanningAnotherRootKeepsTheEntriesOfTheFirstOne() {
        Path other = Path.of("/elsewhere").toAbsolutePath();
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        files.clear();
        files.add(new FileStat(other.resolve("b/1.flac"), 10, 1));
        newIndex().open(other);
        files.clear();
        files.add(file("a/1.flac", 10, 1));
        reads.clear();

        newIndex().open(ROOT);

        assertTrue(reads.isEmpty());
        assertEquals(1, lastSnapshot().trackCount());
        assertEquals(2, stored.size());
    }

    @Test
    void unreachableRootKeepsTheCacheAndReportsFailure() {
        files.add(file("a/1.flac", 10, 1));
        newIndex().open(ROOT);
        saves.clear();
        snapshots.clear();
        files.clear();
        walkSucceeds = false;

        newIndex().open(ROOT);

        assertTrue(saves.isEmpty());
        assertEquals(1, stored.size());
        assertEquals(1, snapshots.size());
        assertEquals(IndexProgress.FAILED, lastProgress());
    }

    @Test
    void unreachableRootWithoutACacheStillSavesNothing() {
        walkSucceeds = false;

        newIndex().open(ROOT);

        assertTrue(saves.isEmpty());
        assertTrue(lastProgress().failed());
    }
}
