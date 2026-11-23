package com.marianocarrizo.dynamusic.controller;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class FileOpener {

    private final BiConsumer<File, String> playNow;
    private final Consumer<File> showFolder;
    private final Consumer<File> revealFile;

    FileOpener(BiConsumer<File, String> playNow, Consumer<File> showFolder, Consumer<File> revealFile) {
        this.playNow = playNow;
        this.showFolder = showFolder;
        this.revealFile = revealFile;
    }

    void open(List<File> items) {
        OpenIntent intent = OpenIntent.of(items);
        intent.folder().ifPresent(showFolder);
        intent.track().ifPresent(track -> {
            revealFile.accept(track);
            playNow.accept(track, null);
        });
    }
}
