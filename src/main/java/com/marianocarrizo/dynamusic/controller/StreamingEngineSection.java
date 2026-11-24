package com.marianocarrizo.dynamusic.controller;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

final class StreamingEngineSection {

    static final String CHECKBOX_LABEL = "Streaming engine (experimental)";
    static final String HINT = "Applies to the next track";
    private static final double SPACING = 4;

    private StreamingEngineSection() {}

    static Node create(boolean enabled, Consumer<Boolean> onToggled) {
        CheckBox checkBox = new CheckBox(CHECKBOX_LABEL);
        checkBox.setSelected(enabled);
        checkBox.selectedProperty().addListener((observable, previous, selected) -> onToggled.accept(selected));
        Label hint = new Label(HINT);
        hint.getStyleClass().add("now-playing-detail");
        return new VBox(SPACING, checkBox, hint);
    }
}
