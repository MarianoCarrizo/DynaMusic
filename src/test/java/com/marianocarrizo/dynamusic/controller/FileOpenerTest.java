package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileOpenerTest {

    @TempDir Path dir;

    private final List<String> events = new ArrayList<>();
    private final FileOpener opener = new FileOpener(
            (file, title) -> events.add("play " + file.getName() + " " + title),
            folder -> events.add("folder " + folder.getName()),
            file -> events.add("reveal " + file.getName()));

    @Test
    void aFileIsRevealedThenPlayedWithoutATitle() throws IOException {
        File song = Files.createFile(dir.resolve("a.mp3")).toFile();

        opener.open(List.of(song));

        assertEquals(List.of("reveal a.mp3", "play a.mp3 null"), events);
    }

    @Test
    void aFolderOnlyBecomesTheLibrary() throws IOException {
        File folder = Files.createDirectory(dir.resolve("music")).toFile();

        opener.open(List.of(folder));

        assertEquals(List.of("folder music"), events);
    }

    @Test
    void folderComesBeforeTheFile() throws IOException {
        File folder = Files.createDirectory(dir.resolve("music")).toFile();
        File song = Files.createFile(dir.resolve("a.wav")).toFile();

        opener.open(List.of(song, folder));

        assertEquals(List.of("folder music", "reveal a.wav", "play a.wav null"), events);
    }

    @Test
    void nothingOpenableDoesNothing() {
        opener.open(List.of());

        assertEquals(List.of(), events);
    }
}
