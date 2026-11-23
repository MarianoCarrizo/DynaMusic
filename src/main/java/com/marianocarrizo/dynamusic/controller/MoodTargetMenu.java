package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.List;
import java.util.function.Consumer;

final class MoodTargetMenu extends Menu {

    private static final List<TrackType> MENU_ORDER =
            List.of(TrackType.PAUSED, TrackType.NORMAL, TrackType.RAGED, TrackType.ADVANTAGE);

    MoodTargetMenu(String label, Consumer<TrackType> onPicked) {
        super(label);
        for (TrackType type : MENU_ORDER) {
            MenuItem item = new MenuItem(DisplayNames.capitalize(type.name()));
            item.setOnAction(e -> onPicked.accept(type));
            getItems().add(item);
        }
    }
}
