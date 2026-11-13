package com.marianocarrizo.dynamusic.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class DataFolderMigration {

    static final String LEGACY_FOLDER_NAME = "DynamicMusic";
    private static final String SESSION_FILE = "session.json";

    private DataFolderMigration() {
    }

    public static void migrateIfNeeded() {
        String osName = System.getProperty("os.name", "");
        String appdata = System.getenv("APPDATA");
        String home = System.getProperty("user.home");
        migrate(legacyDir(osName, appdata, home), YouTubeDownloader.appDir(osName, appdata, home));
    }

    static Path legacyDir(String osName, String appdata, String home) {
        return (YtDlpManager.isWindows(osName) && appdata != null && !appdata.isBlank())
                ? Paths.get(appdata, LEGACY_FOLDER_NAME)
                : Paths.get(home, LEGACY_FOLDER_NAME);
    }

    static boolean migrate(Path oldDir, Path newDir) {
        if (!Files.isDirectory(oldDir)) {
            return false;
        }
        if (Files.exists(newDir)) {
            return hasSession(newDir) ? false : mergeInto(oldDir, newDir);
        }
        try {
            Files.move(oldDir, newDir);
            return true;
        } catch (IOException moveFailure) {
            System.err.println("[migration] could not move " + oldDir + ", copying instead: " + moveFailure);
            return copyThenDelete(oldDir, newDir);
        }
    }

    private static boolean hasSession(Path dir) {
        return Files.exists(dir.resolve(SESSION_FILE));
    }

    private static boolean mergeInto(Path oldDir, Path newDir) {
        if (!hasSession(oldDir)) {
            return false;
        }
        try {
            copyTree(oldDir, newDir);
        } catch (IOException | UncheckedIOException copyFailure) {
            System.err.println("[migration] could not merge " + oldDir + " into " + newDir + ": " + copyFailure);
            return false;
        }
        deleteQuietly(oldDir);
        return true;
    }

    private static boolean copyThenDelete(Path oldDir, Path newDir) {
        try {
            copyTree(oldDir, newDir);
        } catch (IOException | UncheckedIOException copyFailure) {
            System.err.println("[migration] could not copy " + oldDir + ", keeping it in place: " + copyFailure);
            deleteQuietly(newDir);
            return false;
        }
        deleteQuietly(oldDir);
        return true;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path destination = target.resolve(source.relativize(file).toString());
                if (!Files.exists(destination)) {
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteQuietly(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return;
        }
    }
}
