package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryScannerTest {

    @TempDir Path dir;

    private static List<String> leafNames(TreeItem<File> node) {
        List<String> names = new ArrayList<>();
        if (node.getChildren().isEmpty()) names.add(node.getValue().getName());
        node.getChildren().forEach(child -> names.addAll(leafNames(child)));
        return names;
    }

    private Path nested(int levels) throws IOException {
        Path folder = dir;
        for (int level = 1; level <= levels; level++) {
            folder = Files.createDirectory(folder.resolve("d" + level));
        }
        return folder;
    }

    private TreeItem<File> scan(String filter) {
        return new LibraryScanner(new LibraryFilter(filter), () -> false).scan(dir.toFile());
    }

    @Test
    void collectsAudioFilesAndSkipsEverythingElse() throws IOException {
        Files.createFile(dir.resolve("a.mp3"));
        Files.createFile(dir.resolve("notes.txt"));
        Files.createFile(Files.createDirectory(dir.resolve("album")).resolve("b.flac"));
        Files.createDirectory(dir.resolve("empty"));

        assertEquals(List.of("b.flac", "a.mp3"), leafNames(scan("")));
    }

    @Test
    void filterKeepsOnlyMatchingNames() throws IOException {
        Files.createFile(dir.resolve("rain.mp3"));
        Files.createFile(dir.resolve("sun.mp3"));

        assertEquals(List.of("rain.mp3"), leafNames(scan("rai")));
    }

    @Test
    void foldersAtTheDepthLimitAreScanned() throws IOException {
        Files.createFile(nested(LibraryScanner.MAX_DEPTH).resolve("deep.mp3"));

        assertEquals(List.of("deep.mp3"), leafNames(scan("")));
    }

    @Test
    void foldersBeyondTheDepthLimitAreSkipped() throws IOException {
        Files.createFile(nested(LibraryScanner.MAX_DEPTH + 1).resolve("tooDeep.mp3"));

        assertTrue(scan("").getChildren().isEmpty());
    }

    @Test
    void cancelledScanStopsWithoutDescending() throws IOException {
        Files.createFile(dir.resolve("a.mp3"));

        TreeItem<File> root = new LibraryScanner(LibraryFilter.NONE, () -> true).scan(dir.toFile());

        assertTrue(root.getChildren().isEmpty());
    }

    @Test
    void symbolicLinkLoopsAreNotFollowed() throws IOException {
        Path album = Files.createDirectory(dir.resolve("album"));
        Files.createFile(album.resolve("a.mp3"));
        try {
            Files.createSymbolicLink(album.resolve("loop"), dir);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("Symbolic links are not available here");
        }

        assertEquals(List.of("a.mp3"), leafNames(scan("")));
    }

    private static List<String> folderNames(TreeItem<File> node) {
        List<String> names = new ArrayList<>();
        node.getChildren().stream().filter(child -> child.getValue().isDirectory())
                .forEach(child -> names.add(child.getValue().getName()));
        return names;
    }

    @Test
    void matchingFolderIsKeptWithAllItsContents() throws IOException {
        Path album = Files.createDirectory(dir.resolve("Red Hot Chili Peppers 24bit"));
        Files.createFile(album.resolve("one.mp3"));
        Files.createFile(album.resolve("two.mp3"));
        Files.createFile(Files.createDirectory(dir.resolve("Other")).resolve("three.mp3"));

        TreeItem<File> root = scan("red hot");

        assertEquals(List.of("Red Hot Chili Peppers 24bit"), folderNames(root));
        assertEquals(List.of("one.mp3", "two.mp3"), leafNames(root));
    }

    @Test
    void matchingFolderKeepsItsAncestorsAndLeavesItCollapsed() throws IOException {
        Path outer = Files.createDirectory(dir.resolve("Rock"));
        Path album = Files.createDirectory(outer.resolve("Red Hot"));
        Files.createFile(album.resolve("one.mp3"));

        TreeItem<File> rock = scan("red").getChildren().get(0);

        assertEquals("Rock", rock.getValue().getName());
        assertTrue(rock.isExpanded());
        assertEquals("Red Hot", rock.getChildren().get(0).getValue().getName());
        assertFalse(rock.getChildren().get(0).isExpanded());
    }

    @Test
    void matchingTrackKeepsAndExpandsItsAncestors() throws IOException {
        Path album = Files.createDirectory(dir.resolve("album"));
        Files.createFile(album.resolve("rain.mp3"));
        Files.createFile(album.resolve("sun.mp3"));

        TreeItem<File> folder = scan("rain").getChildren().get(0);

        assertTrue(folder.isExpanded());
        assertEquals(List.of("rain.mp3"), leafNames(folder));
    }

    @Test
    void matchingFolderWithoutAudioIsDropped() throws IOException {
        Files.createDirectory(dir.resolve("Red Hot"));

        assertTrue(scan("red").getChildren().isEmpty());
    }

    @Test
    void folderMatchIgnoresCaseAndAccents() throws IOException {
        Files.createFile(Files.createDirectory(dir.resolve("Café Tacvba")).resolve("a.mp3"));

        assertEquals(List.of("Café Tacvba"), folderNames(scan("CAFE")));
    }

    @Test
    void emptyFilterLeavesFoldersCollapsed() throws IOException {
        Files.createFile(Files.createDirectory(dir.resolve("album")).resolve("a.mp3"));

        assertFalse(scan("").getChildren().get(0).isExpanded());
    }
}
