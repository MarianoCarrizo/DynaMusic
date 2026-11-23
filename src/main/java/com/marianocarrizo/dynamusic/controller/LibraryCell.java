package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.List;
import java.util.Optional;

final class LibraryCell extends TreeCell<File> {

    interface Host {
        void playNow(File file, String title);

        void assignTrack(TrackType type, File file);

        List<String> playlistNames();

        void addToPlaylist(String playlistName, File source);

        void createPlaylistFromFolder(File folder);

        Optional<String> promptNewPlaylistName();
    }

    private final Host host;
    private final ContextMenu fileMenu = new ContextMenu();
    private final ContextMenu folderMenu = new ContextMenu();
    private final Menu filePlaylistMenu = new Menu("Add to playlist");
    private final Menu folderPlaylistMenu = new Menu("Add to playlist");

    LibraryCell(Host host) {
        this.host = host;
        Menu assign = new Menu("Assign to");
        assign.getItems().addAll(
                assignItem("Paused", TrackType.PAUSED),
                assignItem("Normal", TrackType.NORMAL),
                assignItem("Raged", TrackType.RAGED),
                assignItem("Advantage", TrackType.ADVANTAGE));
        MenuItem playNow = new MenuItem("Play now");
        playNow.setOnAction(e -> {
            File f = getItem();
            if (f != null && f.isFile()) host.playNow(f, null);
        });
        fileMenu.getItems().addAll(playNow, assign, filePlaylistMenu);
        fileMenu.setOnShowing(e -> fillPlaylistMenu(filePlaylistMenu));

        MenuItem newFromFolder = new MenuItem("New playlist from folder");
        newFromFolder.setOnAction(e -> host.createPlaylistFromFolder(getItem()));
        folderMenu.getItems().addAll(folderPlaylistMenu, newFromFolder);
        folderMenu.setOnShowing(e -> fillPlaylistMenu(folderPlaylistMenu));

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

    private void fillPlaylistMenu(Menu target) {
        target.getItems().clear();
        for (String playlistName : host.playlistNames()) {
            MenuItem entry = new MenuItem(playlistName);
            entry.setOnAction(e -> host.addToPlaylist(playlistName, getItem()));
            target.getItems().add(entry);
        }
        if (!target.getItems().isEmpty()) target.getItems().add(new SeparatorMenuItem());
        MenuItem created = new MenuItem("New playlist...");
        created.setOnAction(e -> host.promptNewPlaylistName().ifPresent(
                name -> host.addToPlaylist(name, getItem())));
        target.getItems().add(created);
    }

    private MenuItem assignItem(String label, TrackType type) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            File f = getItem();
            if (f != null && f.isFile()) host.assignTrack(type, f);
        });
        return item;
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
