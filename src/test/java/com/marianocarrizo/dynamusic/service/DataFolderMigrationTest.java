package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataFolderMigrationTest {

    @TempDir
    Path root;

    private Path populate(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("library"));
        Files.createDirectories(dir.resolve("yt-cache"));
        Files.createDirectories(dir.resolve("bin"));
        Files.createDirectories(dir.resolve("updates"));
        Files.writeString(dir.resolve("session.json"), "{}");
        Files.writeString(dir.resolve("library").resolve("a.mp3"), "audio");
        return dir;
    }

    @Test
    void movesOldFolderWhenNewIsMissing() throws IOException {
        Path oldDir = populate(root.resolve("DynamicMusic"));
        Path newDir = root.resolve("DynaMusic");

        assertTrue(DataFolderMigration.migrate(oldDir, newDir));

        assertFalse(Files.exists(oldDir));
        assertEquals("{}", Files.readString(newDir.resolve("session.json")));
        assertEquals("audio", Files.readString(newDir.resolve("library").resolve("a.mp3")));
        assertTrue(Files.isDirectory(newDir.resolve("yt-cache")));
        assertTrue(Files.isDirectory(newDir.resolve("bin")));
        assertTrue(Files.isDirectory(newDir.resolve("updates")));
    }

    @Test
    void leavesBothFoldersUntouchedWhenNewExists() throws IOException {
        Path oldDir = populate(root.resolve("DynamicMusic"));
        Path newDir = Files.createDirectories(root.resolve("DynaMusic"));
        Files.writeString(newDir.resolve("session.json"), "new");

        assertFalse(DataFolderMigration.migrate(oldDir, newDir));

        assertEquals("{}", Files.readString(oldDir.resolve("session.json")));
        assertEquals("new", Files.readString(newDir.resolve("session.json")));
        assertFalse(Files.exists(newDir.resolve("library")));
    }

    @Test
    void mergesOldDataIntoANewFolderThatHasNoSession() throws IOException {
        Path oldDir = populate(root.resolve("DynamicMusic"));
        Path newDir = Files.createDirectories(root.resolve("DynaMusic").resolve("bin"));
        Files.writeString(newDir.resolve("yt-dlp.exe"), "fresh");
        Files.writeString(oldDir.resolve("bin").resolve("yt-dlp.exe"), "stale");
        Path newRoot = newDir.getParent();

        assertTrue(DataFolderMigration.migrate(oldDir, newRoot));

        assertFalse(Files.exists(oldDir));
        assertEquals("{}", Files.readString(newRoot.resolve("session.json")));
        assertEquals("audio", Files.readString(newRoot.resolve("library").resolve("a.mp3")));
        assertEquals("fresh", Files.readString(newRoot.resolve("bin").resolve("yt-dlp.exe")));
    }

    @Test
    void doesNothingWhenNeitherExists() {
        Path oldDir = root.resolve("DynamicMusic");
        Path newDir = root.resolve("DynaMusic");

        assertFalse(DataFolderMigration.migrate(oldDir, newDir));

        assertFalse(Files.exists(oldDir));
        assertFalse(Files.exists(newDir));
    }

    @Test
    void legacyDirFollowsTheSamePlatformRulesAsTheCurrentOne() {
        assertEquals(Paths.get("C:\\Users\\u\\AppData\\Roaming", "DynamicMusic"),
                DataFolderMigration.legacyDir("Windows 11", "C:\\Users\\u\\AppData\\Roaming", "C:\\Users\\u"));
        assertEquals(Paths.get("/home/u", "DynamicMusic"),
                DataFolderMigration.legacyDir("Linux", "/stray/appdata", "/home/u"));
    }
}
