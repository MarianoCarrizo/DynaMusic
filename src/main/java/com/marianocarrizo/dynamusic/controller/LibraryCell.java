package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.List;

final class LibraryCell extends TreeCell<File> {

    interface Host extends FileTrackMenuItems.Host {
        void createPlaylistFromFolder(File folder);
    }

    private final Host host;
    private final ContextMenu fileMenu = new ContextMenu();
    private final ContextMenu folderMenu = new ContextMenu();

    LibraryCell(Host host) {
        this.host = host;
        fileMenu.getItems().addAll(FileTrackMenuItems.create(host, this::getItem, () -> null));

        MenuItem newFromFolder = new MenuItem("New playlist from folder");
        newFromFolder.setOnAction(e -> host.createPlaylistFromFolder(getItem()));
        folderMenu.getItems().addAll(new PlaylistTargetMenu(host, this::getItem), newFromFolder);

        setOnDragDetected(e -> {
            File f = getItem();
            if (f == null) return;
            Dragboard db = startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(f));
            db.setContent(content);
            e.consume();
        });
    }

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setContextMenu(null);
            return;
        }
        setText(item.isDirectory() ? "📁 " + item.getName() : item.getName());
        setContextMenu(item.isDirectory() ? folderMenu : fileMenu);
    }
}
