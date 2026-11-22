package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFileCollectorTest {

    @TempDir
    Path root;

    @Test
    void collectsSupportedFilesRecursivelyAndIgnoresTheRest() throws IOException {
        touch("one.mp3");
        touch("notes.txt");
        touch("cover.png");
        touch("sub/two.FLAC");
        touch("sub/deeper/three.wav");

        List<String> names = namesOf(AudioFileCollector.collect(root.toFile()));

        assertEquals(List.of("one.mp3", "three.wav", "two.FLAC"), names);
    }

    @Test
    void ordersNumberedNamesNaturallyAndIgnoresCase() throws IOException {
        touch("track10.mp3");
        touch("Track2.mp3");
        touch("track1.mp3");

        List<String> names = namesOf(AudioFileCollector.collect(root.toFile()));

        assertEquals(List.of("track1.mp3", "Track2.mp3", "track10.mp3"), names);
    }

    @Test
    void ordersByFolderBeforeFileNameWithinTheSameLevel() throws IOException {
        touch("b/01.mp3");
        touch("a/02.mp3");
        touch("a/01.mp3");

        List<String> paths = AudioFileCollector.collect(root.toFile()).stream()
                .map(file -> root.relativize(file.toPath()).toString().replace(File.separatorChar, '/'))
                .toList();

        assertEquals(List.of("a/01.mp3", "a/02.mp3", "b/01.mp3"), paths);
    }

    @Test
    void returnsASingleSupportedFileAndNothingForOtherFiles() throws IOException {
        File song = touch("song.mp3");
        File text = touch("song.txt");

        assertEquals(List.of(song), AudioFileCollector.collect(song));
        assertTrue(AudioFileCollector.collect(text).isEmpty());
    }

    @Test
    void returnsNothingForMissingPathsAndEmptyFolders() throws IOException {
        Files.createDirectories(root.resolve("empty"));

        assertTrue(AudioFileCollector.collect(root.resolve("empty").toFile()).isEmpty());
        assertTrue(AudioFileCollector.collect(root.resolve("missing.mp3").toFile()).isEmpty());
        assertTrue(AudioFileCollector.collect(null).isEmpty());
    }

    @Test
    void skipsEntriesThatCannotBeReadInsteadOfFailing() {
        AudioFileCollector.CollectingVisitor visitor = new AudioFileCollector.CollectingVisitor();

        FileVisitResult result = visitor.visitFileFailed(root.resolve("locked"), new AccessDeniedException("locked"));

        assertEquals(FileVisitResult.CONTINUE, result);
    }

    private File touch(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
        return file.toFile();
    }

    private static List<String> namesOf(List<File> files) {
        return files.stream().map(File::getName).toList();
    }
}
