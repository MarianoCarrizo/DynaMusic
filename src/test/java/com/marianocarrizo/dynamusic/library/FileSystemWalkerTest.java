package com.marianocarrizo.dynamusic.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemWalkerTest {

    @TempDir
    Path root;

    private final FileSystemWalker walker = new FileSystemWalker();

    private Path touch(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private List<FileStat> walkAll() {
        List<FileStat> found = new ArrayList<>();
        walker.walk(root, found::add, () -> false);
        return found;
    }

    @Test
    void reportsAudioFilesWithSizeAndModifiedTime() throws IOException {
        Path track = touch("album/one.flac", "12345");

        List<FileStat> found = walkAll();

        assertEquals(1, found.size());
        assertEquals(track, found.get(0).path());
        assertEquals(5, found.get(0).size());
        assertEquals(Files.getLastModifiedTime(track).toMillis(), found.get(0).modified());
    }

    @Test
    void ignoresFilesThatAreNotAudio() throws IOException {
        touch("album/cover.jpg", "x");
        touch("album/notes.txt", "x");
        touch("album/two.mp3", "x");

        List<String> names = walkAll().stream().map(stat -> stat.path().getFileName().toString()).toList();

        assertEquals(List.of("two.mp3"), names);
    }

    @Test
    void doesNotDescendPastTheDepthLimit() throws IOException {
        StringBuilder deep = new StringBuilder();
        for (int level = 0; level <= FileSystemWalker.MAX_DEPTH; level++) deep.append("d").append(level).append('/');
        touch(deep + "buried.flac", "x");
        touch("shallow.flac", "x");

        List<String> names = walkAll().stream().map(stat -> stat.path().getFileName().toString()).toList();

        assertEquals(List.of("shallow.flac"), names);
    }

    @Test
    void stopsWhenCancelled() throws IOException {
        for (int i = 0; i < 5; i++) touch("a/" + i + ".flac", "x");
        List<FileStat> found = new ArrayList<>();

        walker.walk(root, found::add, () -> found.size() >= 2);

        assertEquals(2, found.size());
    }

    @Test
    void missingRootIsNotAnError() {
        List<FileStat> found = new ArrayList<>();

        walker.walk(root.resolve("absent"), found::add, () -> false);

        assertTrue(found.isEmpty());
    }
}
