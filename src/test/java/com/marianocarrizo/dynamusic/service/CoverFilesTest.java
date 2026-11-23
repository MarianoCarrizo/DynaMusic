package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverFilesTest {

    private static void touch(Path dir, String name) throws IOException {
        Files.createFile(dir.resolve(name));
    }

    @Test
    void emptyFolderHasNoCover(@TempDir Path dir) {
        assertTrue(CoverFiles.find(dir.toFile()).isEmpty());
    }

    @Test
    void missingFolderHasNoCover(@TempDir Path dir) {
        assertTrue(CoverFiles.find(dir.resolve("nope").toFile()).isEmpty());
        assertTrue(CoverFiles.find(null).isEmpty());
    }

    @Test
    void findsCoverIgnoringCase(@TempDir Path dir) throws IOException {
        touch(dir, "COVER.JPG");

        assertEquals("COVER.JPG", CoverFiles.find(dir.toFile()).map(File::getName).orElse(""));
    }

    @Test
    void coverBeatsFolderBeatsFront(@TempDir Path dir) throws IOException {
        touch(dir, "front.jpg");
        touch(dir, "folder.jpg");
        assertEquals("folder.jpg", CoverFiles.find(dir.toFile()).map(File::getName).orElse(""));

        touch(dir, "cover.png");
        assertEquals("cover.png", CoverFiles.find(dir.toFile()).map(File::getName).orElse(""));
    }

    @Test
    void jpgBeatsJpegBeatsPngForTheSameName(@TempDir Path dir) throws IOException {
        touch(dir, "cover.png");
        touch(dir, "cover.jpeg");
        assertEquals("cover.jpeg", CoverFiles.find(dir.toFile()).map(File::getName).orElse(""));

        touch(dir, "cover.jpg");
        assertEquals("cover.jpg", CoverFiles.find(dir.toFile()).map(File::getName).orElse(""));
    }

    @Test
    void ignoresOtherImagesAndFolders(@TempDir Path dir) throws IOException {
        touch(dir, "artist.jpg");
        Files.createDirectory(dir.resolve("cover.jpg"));

        Optional<File> found = CoverFiles.find(dir.toFile());

        assertTrue(found.isEmpty());
    }

    @Test
    void onlyMp3AndM4aMayHaveEmbeddedArt() {
        assertTrue(CoverFiles.mayHaveEmbeddedArt(new File("a/Song.MP3")));
        assertTrue(CoverFiles.mayHaveEmbeddedArt(new File("a/Song.m4a")));
        assertFalse(CoverFiles.mayHaveEmbeddedArt(new File("a/Song.flac")));
        assertFalse(CoverFiles.mayHaveEmbeddedArt(new File("a/Song.wav")));
        assertFalse(CoverFiles.mayHaveEmbeddedArt(new File("a/Song")));
    }
}
