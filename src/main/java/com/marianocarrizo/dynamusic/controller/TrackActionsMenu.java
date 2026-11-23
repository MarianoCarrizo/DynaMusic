package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

import java.io.File;

final class TrackActionsMenu {

    interface Host extends PlaylistTargetMenu.Host {
        void assignTrack(TrackType type, File file);
    }

    private final ContextMenu menu = new ContextMenu();
    private File track;

    TrackActionsMenu(Host host) {
        menu.getItems().addAll(
                new PlaylistTargetMenu(host, () -> track),
                new MoodTargetMenu("Use in mood", type -> host.assignTrack(type, track)));
    }

    void show(Node anchor, File file, double screenX, double screenY) {
        track = file;
        menu.show(anchor, screenX, screenY);
    }
}
