package com.marianocarrizo.dynamusic.controller;

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

class LibraryRootSelectionTest {

    @TempDir
    Path temp;

    private final LibraryRootSelection selection = new LibraryRootSelection();

    @Test
    void startsWithoutARoot() {
        assertEquals(Optional.empty(), selection.current());
        assertEquals(null, selection.property().get());
    }

    @Test
    void choosingAFolderSetsTheRoot() throws IOException {
        File music = Files.createDirectory(temp.resolve("music")).toFile();

        assertTrue(selection.choose(music));

        assertEquals(Optional.of(music), selection.current());
        assertEquals(music, selection.property().get());
    }

    @Test
    void choosingTheSameFolderAgainReportsNoChange() throws IOException {
        File music = Files.createDirectory(temp.resolve("music")).toFile();
        selection.choose(music);

        assertFalse(selection.choose(music));
    }

    @Test
    void choosingNothingOrAFileKeepsTheRoot() throws IOException {
        File music = Files.createDirectory(temp.resolve("music")).toFile();
        File song = Files.createFile(temp.resolve("song.flac")).toFile();
        selection.choose(music);

        assertFalse(selection.choose(null));
        assertFalse(selection.choose(song));

        assertEquals(Optional.of(music), selection.current());
    }

    @Test
    void restoreUsesAPersistedFolder() throws IOException {
        File music = Files.createDirectory(temp.resolve("music")).toFile();

        selection.restore(music.getAbsolutePath());

        assertEquals(Optional.of(music), selection.current());
    }

    @Test
    void restoreIgnoresMissingBlankAndNullPaths() {
        selection.restore(null);
        selection.restore("");
        selection.restore(temp.resolve("gone").toString());

        assertEquals(Optional.empty(), selection.current());
    }
}
