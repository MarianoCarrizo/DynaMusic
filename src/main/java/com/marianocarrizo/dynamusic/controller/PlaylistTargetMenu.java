package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class PlaylistTargetMenu extends Menu {

    interface Host {
        List<String> playlistNames();

        void addToPlaylist(String playlistName, File source);

        Optional<String> promptNewPlaylistName();
    }

    private final Host host;
    private final Supplier<File> source;
    private final Supplier<String> excludedPlaylist;

    PlaylistTargetMenu(Host host, Supplier<File> source) {
        this(host, source, () -> null);
    }

    PlaylistTargetMenu(Host host, Supplier<File> source, Supplier<String> excludedPlaylist) {
        super("Add to playlist");
        this.host = host;
        this.source = source;
        this.excludedPlaylist = excludedPlaylist;
        getItems().add(newPlaylistItem());
        setOnShowing(e -> fill());
    }

    private void fill() {
        getItems().clear();
        for (String playlistName : host.playlistNames()) {
            if (playlistName.equalsIgnoreCase(excludedPlaylist.get())) continue;
            MenuItem entry = new MenuItem(playlistName);
            entry.setOnAction(e -> host.addToPlaylist(playlistName, source.get()));
            getItems().add(entry);
        }
        if (!getItems().isEmpty()) getItems().add(new SeparatorMenuItem());
        getItems().add(newPlaylistItem());
    }

    private MenuItem newPlaylistItem() {
        MenuItem created = new MenuItem("New playlist...");
        created.setOnAction(e -> host.promptNewPlaylistName().ifPresent(
                name -> host.addToPlaylist(name, source.get())));
        return created;
    }
}
