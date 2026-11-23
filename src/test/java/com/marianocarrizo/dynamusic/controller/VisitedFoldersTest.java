package com.marianocarrizo.dynamusic.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitedFoldersTest {

    @TempDir Path dir;

    private final VisitedFolders visited = new VisitedFolders();

    @Test
    void firstVisitIsAcceptedAndRepeatsAreRejected() throws IOException {
        Path folder = Files.createDirectory(dir.resolve("music"));

        assertTrue(visited.firstVisit(folder.toFile()));
        assertFalse(visited.firstVisit(folder.toFile()));
    }

    @Test
    void differentFoldersAreIndependent() throws IOException {
        Path first = Files.createDirectory(dir.resolve("a"));
        Path second = Files.createDirectory(dir.resolve("b"));

        assertTrue(visited.firstVisit(first.toFile()));
        assertTrue(visited.firstVisit(second.toFile()));
    }

    @Test
    void sameFolderReachedThroughDifferentSpellingsIsRejected() throws IOException {
        Path folder = Files.createDirectory(dir.resolve("music"));
        Path other = Files.createDirectory(dir.resolve("other"));
        Path roundAbout = other.resolve("..").resolve("music");

        assertTrue(visited.firstVisit(folder.toFile()));
        assertFalse(visited.firstVisit(roundAbout.toFile()));
    }

    @Test
    void folderThatCannotBeResolvedIsRejected() {
        assertFalse(visited.firstVisit(dir.resolve("missing").toFile()));
    }
}
