package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;

import java.io.File;

final class TrackActionsMenu {

    interface Host extends PlaylistTargetMenu.Host {
        void assignTrack(TrackType type, File file);

        ObservableBooleanValue moodActionsVisible();
    }

    private final ContextMenu menu = new ContextMenu();
    private File track;

    TrackActionsMenu(Host host) {
        MoodTargetMenu useInMood = new MoodTargetMenu("Use in mood", type -> host.assignTrack(type, track));
        useInMood.visibleProperty().bind(host.moodActionsVisible());
        menu.getItems().addAll(new PlaylistTargetMenu(host, () -> track), useInMood);
    }

    void show(Node anchor, File file, double screenX, double screenY) {
        track = file;
        menu.show(anchor, screenX, screenY);
    }
}
