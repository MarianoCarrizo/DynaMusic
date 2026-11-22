package com.marianocarrizo.dynamusic.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AudioFileCollector {

    private static final List<String> AUDIO_EXTENSIONS = List.of(".mp3", ".wav", ".flac");

    private AudioFileCollector() {}

    public static boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return AUDIO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    public static List<File> collect(File fileOrFolder) {
        if (fileOrFolder == null) return List.of();
        if (fileOrFolder.isFile()) {
            return isAudioFile(fileOrFolder) ? List.of(fileOrFolder) : List.of();
        }
        if (!fileOrFolder.isDirectory()) return List.of();
        return collectFromFolder(fileOrFolder.toPath());
    }

    public static List<File> collectAll(List<File> filesOrFolders) {
        List<File> collected = new ArrayList<>();
        for (File entry : filesOrFolders) collected.addAll(collect(entry));
        return collected;
    }

    private static List<File> collectFromFolder(Path folder) {
        CollectingVisitor visitor = new CollectingVisitor();
        try {
            Files.walkFileTree(folder, visitor);
        } catch (IOException e) {
            System.err.println("Could not read folder: " + e.getMessage());
        }
        return visitor.sortedRelativeTo(folder);
    }

    static final class CollectingVisitor extends SimpleFileVisitor<Path> {
        private final List<File> found = new ArrayList<>();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            File candidate = file.toFile();
            if (attrs.isRegularFile() && isAudioFile(candidate)) found.add(candidate);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) {
            return FileVisitResult.CONTINUE;
        }

        List<File> sortedRelativeTo(Path folder) {
            return found.stream()
                    .sorted(Comparator.comparing(file -> folder.relativize(file.toPath()), AudioFileCollector::comparePaths))
                    .toList();
        }
    }

    private static int comparePaths(Path a, Path b) {
        int shared = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = 0; i < shared; i++) {
            int byName = compareNatural(a.getName(i).toString(), b.getName(i).toString());
            if (byName != 0) return byName;
        }
        return Integer.compare(a.getNameCount(), b.getNameCount());
    }

    static int compareNatural(String a, String b) {
        String left = a.toLowerCase(Locale.ROOT);
        String right = b.toLowerCase(Locale.ROOT);
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            if (Character.isDigit(left.charAt(i)) && Character.isDigit(right.charAt(j))) {
                int endLeft = digitRunEnd(left, i);
                int endRight = digitRunEnd(right, j);
                int byNumber = compareDigitRuns(left.substring(i, endLeft), right.substring(j, endRight));
                if (byNumber != 0) return byNumber;
                i = endLeft;
                j = endRight;
            } else {
                int byChar = Character.compare(left.charAt(i), right.charAt(j));
                if (byChar != 0) return byChar;
                i++;
                j++;
            }
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }

    private static int digitRunEnd(String text, int start) {
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        return end;
    }

    private static int compareDigitRuns(String a, String b) {
        String left = a.replaceFirst("^0+(?=.)", "");
        String right = b.replaceFirst("^0+(?=.)", "");
        if (left.length() != right.length()) return Integer.compare(left.length(), right.length());
        return left.compareTo(right);
    }
}
