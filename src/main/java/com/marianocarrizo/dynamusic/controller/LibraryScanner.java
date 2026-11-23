package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BooleanSupplier;

final class LibraryScanner {

    static final int MAX_DEPTH = 12;

    private final String filter;
    private final BooleanSupplier cancelled;
    private final VisitedFolders visited = new VisitedFolders();

    LibraryScanner(String filter, BooleanSupplier cancelled) {
        this.filter = filter;
        this.cancelled = cancelled;
    }

    TreeItem<File> scan(File rootFolder) {
        TreeItem<File> root = new TreeItem<>(rootFolder);
        root.setExpanded(true);
        visited.firstVisit(rootFolder);
        populate(root, rootFolder, 0);
        return root;
    }

    private void populate(TreeItem<File> parent, File folder, int depth) {
        if (cancelled.getAsBoolean()) return;
        File[] entries = folder.listFiles();
        if (entries == null) return;
        Arrays.sort(entries, Comparator.comparing(File::isFile).thenComparing(f -> f.getName().toLowerCase()));
        for (File entry : entries) {
            if (entry.isDirectory()) {
                addFolder(parent, entry, depth + 1);
            } else if (matches(entry)) {
                parent.getChildren().add(new TreeItem<>(entry));
            }
        }
    }

    private void addFolder(TreeItem<File> parent, File folder, int depth) {
        if (depth > MAX_DEPTH || Files.isSymbolicLink(folder.toPath()) || !visited.firstVisit(folder)) return;
        TreeItem<File> item = new TreeItem<>(folder);
        populate(item, folder, depth);
        if (!item.getChildren().isEmpty()) parent.getChildren().add(item);
    }

    private boolean matches(File entry) {
        return AudioFileCollector.isAudioFile(entry)
                && (filter.isEmpty() || entry.getName().toLowerCase().contains(filter));
    }
}
