package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenIntentTest {

    @TempDir Path dir;

    @Test
    void aFolderBecomesTheLibraryAndAFilePlays() throws IOException {
        File folder = Files.createDirectory(dir.resolve("music")).toFile();
        File song = Files.createFile(dir.resolve("a.mp3")).toFile();

        OpenIntent intent = OpenIntent.of(List.of(song, folder));

        assertEquals(folder, intent.folder().orElseThrow());
        assertEquals(song, intent.track().orElseThrow());
    }

    @Test
    void onlyTheFirstAudioFileIsKept() throws IOException {
        File first = Files.createFile(dir.resolve("a.flac")).toFile();
        File second = Files.createFile(dir.resolve("b.wav")).toFile();

        assertEquals(first, OpenIntent.of(List.of(first, second)).track().orElseThrow());
    }

    @Test
    void nonAudioFilesAndMissingPathsAreIgnored() throws IOException {
        File text = Files.createFile(dir.resolve("notes.txt")).toFile();
        File missing = dir.resolve("gone.mp3").toFile();

        assertTrue(OpenIntent.of(List.of(text, missing)).isEmpty());
        assertTrue(OpenIntent.of(List.of()).isEmpty());
    }

    @Test
    void anIntentWithAFolderIsNotEmpty() throws IOException {
        File folder = Files.createDirectory(dir.resolve("music")).toFile();

        assertFalse(OpenIntent.of(List.of(folder)).isEmpty());
    }
}
