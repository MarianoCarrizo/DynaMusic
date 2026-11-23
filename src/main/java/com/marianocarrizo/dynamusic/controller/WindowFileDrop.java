package com.marianocarrizo.dynamusic.controller;

import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

final class WindowFileDrop {

    private WindowFileDrop() {
    }

    static void install(Node window, Consumer<List<File>> onDropped) {
        window.setOnDragOver(event -> {
            if (!carriesOpenableFiles(event)) return;
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        window.setOnDragDropped(event -> {
            boolean accepted = carriesOpenableFiles(event);
            if (accepted) onDropped.accept(event.getDragboard().getFiles());
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private static boolean carriesOpenableFiles(DragEvent event) {
        if (event.getGestureSource() != null || !event.getDragboard().hasFiles()) return false;
        return !OpenIntent.of(event.getDragboard().getFiles()).isEmpty();
    }
}
