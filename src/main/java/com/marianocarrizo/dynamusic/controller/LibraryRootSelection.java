package com.marianocarrizo.dynamusic.controller;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.File;
import java.util.Optional;

final class LibraryRootSelection {

    private final ObjectProperty<File> root = new SimpleObjectProperty<>();

    ReadOnlyObjectProperty<File> property() {
        return root;
    }

    Optional<File> current() {
        return Optional.ofNullable(root.get());
    }

    boolean choose(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        if (root.get() != null && LibraryRoot.isSameFile(root.get(), folder)) return false;
        root.set(folder);
        return true;
    }

    void restore(String path) {
        if (path == null || path.isBlank()) return;
        choose(new File(path));
    }
}
