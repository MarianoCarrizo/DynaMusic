package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.service.AudioFileCollector;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class FileSystemWalker implements FileWalker {

    static final int MAX_DEPTH = 12;

    @Override
    public void walk(Path root, Consumer<FileStat> sink, BooleanSupplier cancelled) {
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH + 1, new Visitor(sink, cancelled));
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("Could not read folder: " + unreadable.getMessage());
        }
    }

    private static final class Visitor extends SimpleFileVisitor<Path> {

        private final Consumer<FileStat> sink;
        private final BooleanSupplier cancelled;

        Visitor(Consumer<FileStat> sink, BooleanSupplier cancelled) {
            this.sink = sink;
            this.cancelled = cancelled;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attrs) {
            return cancelled.getAsBoolean() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (cancelled.getAsBoolean()) return FileVisitResult.TERMINATE;
            if (attrs.isRegularFile() && AudioFileCollector.isAudioFile(file.toFile())) {
                sink.accept(new FileStat(file, attrs.size(), attrs.lastModifiedTime().toMillis()));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) {
            return FileVisitResult.CONTINUE;
        }
    }
}
