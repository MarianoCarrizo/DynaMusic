package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.MenuItem;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

final class FileTrackMenuItems {

    interface Host extends TrackActionsMenu.Host {
        void playNow(File file, String title);

        void enqueue(List<File> files, QueuePlacement placement);
    }

    private FileTrackMenuItems() {
    }

    static List<MenuItem> create(Host host, Supplier<File> track, Supplier<String> excludedPlaylist) {
        MenuItem playNow = new MenuItem("Play now");
        playNow.setOnAction(e -> {
            File file = track.get();
            if (file != null && file.isFile()) host.playNow(file, null);
        });
        MenuItem playNext = queueItem("Play next", QueuePlacement.NEXT, host, track);
        MenuItem addToQueue = queueItem("Add to queue", QueuePlacement.END, host, track);
        MoodTargetMenu assign = new MoodTargetMenu("Assign to", type -> {
            File file = track.get();
            if (file != null && file.isFile()) host.assignTrack(type, file);
        });
        assign.visibleProperty().bind(host.moodActionsVisible());
        return List.of(playNow, playNext, addToQueue, assign, new PlaylistTargetMenu(host, track, excludedPlaylist));
    }

    private static MenuItem queueItem(String text, QueuePlacement placement, Host host, Supplier<File> track) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> {
            File file = track.get();
            if (file != null && file.isFile()) host.enqueue(List.of(file), placement);
        });
        return item;
    }
}
