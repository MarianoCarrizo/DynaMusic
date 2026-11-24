package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;

import java.io.File;
import java.util.Optional;

final class BrowseIndexTrigger {

    private File opened;

    Optional<File> due(AppMode mode, File root) {
        if (mode != AppMode.LISTEN || root == null || root.equals(opened)) return Optional.empty();
        opened = root;
        return Optional.of(root);
    }
}
