package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryRootTest {

    @TempDir Path dir;

    @Test
    void emptyLibraryUsesTheFilesFolder() {
        File file = dir.resolve("album").resolve("a.mp3").toFile();

        assertEquals(Optional.of(dir.resolve("album").toFile()), LibraryRoot.rootFor(null, file));
    }

    @Test
    void existingRootThatContainsTheFileIsKept() {
        File root = dir.resolve("music").toFile();
        File file = dir.resolve("music").resolve("deep").resolve("a.mp3").toFile();

        assertEquals(Optional.of(root), LibraryRoot.rootFor(root, file));
    }

    @Test
    void fileOutsideTheRootReplacesItWithTheFilesFolder() {
        File root = dir.resolve("music").toFile();
        File file = dir.resolve("downloads").resolve("a.mp3").toFile();

        assertEquals(Optional.of(dir.resolve("downloads").toFile()), LibraryRoot.rootFor(root, file));
    }

    @Test
    void siblingFolderWithSharedPrefixIsNotInsideTheRoot() {
        File root = dir.resolve("music").toFile();
        File file = dir.resolve("music-old").resolve("a.mp3").toFile();

        assertFalse(LibraryRoot.contains(root, file));
    }

    @Test
    void unnormalizedPathsStillMatch() {
        File root = dir.resolve("music").toFile();
        File file = dir.resolve("music").resolve("x").resolve("..").resolve("a.mp3").toFile();

        assertTrue(LibraryRoot.contains(root, file));
        assertTrue(LibraryRoot.isSameFile(file, dir.resolve("music").resolve("a.mp3").toFile()));
    }

    @Test
    void fileDirectlyUnderADriveRootNeverBecomesTheLibrary() {
        File driveRoot = File.listRoots()[0];
        File file = new File(driveRoot, "song.mp3");

        assertTrue(LibraryRoot.rootFor(null, file).isEmpty());
    }

    @Test
    void fileUnderADriveRootKeepsTheCurrentLibraryUntouched() {
        File root = dir.resolve("music").toFile();
        File file = new File(File.listRoots()[0], "song.mp3");

        assertTrue(LibraryRoot.rootFor(root, file).isEmpty());
    }

    @Test
    void rootFolderOfALibraryStillContainsItsFiles() {
        File driveRoot = File.listRoots()[0];
        File file = new File(driveRoot, "song.mp3");

        assertEquals(Optional.of(driveRoot), LibraryRoot.rootFor(driveRoot, file));
    }

    @Test
    void detectsFilesystemRoots() {
        assertTrue(LibraryRoot.isFilesystemRoot(File.listRoots()[0]));
        assertFalse(LibraryRoot.isFilesystemRoot(dir.toFile()));
    }
}
