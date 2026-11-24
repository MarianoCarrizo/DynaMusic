package com.marianocarrizo.dynamusic.controller;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

final class StreamingEngineSection {

    static final String CHECKBOX_LABEL = "Streaming engine (experimental)";
    static final String HINT = "Applies to the next track";
    static final String EXCLUSIVE_LABEL = "Exclusive output (bit-perfect)";
    static final String EXCLUSIVE_HINT = "Needs the streaming engine. Falls back to shared output when the device is busy";
    private static final double SPACING = 4;

    private StreamingEngineSection() {}

    static Node create(boolean streaming, boolean exclusive, Consumer<Boolean> onStreaming,
            Consumer<Boolean> onExclusive) {
        CheckBox streamingBox = checkBox(CHECKBOX_LABEL, streaming, onStreaming);
        CheckBox exclusiveBox = checkBox(EXCLUSIVE_LABEL, exclusive, onExclusive);
        exclusiveBox.disableProperty().bind(streamingBox.selectedProperty().not());
        return new VBox(SPACING, streamingBox, hint(HINT), exclusiveBox, hint(EXCLUSIVE_HINT));
    }

    private static CheckBox checkBox(String label, boolean selected, Consumer<Boolean> onToggled) {
        CheckBox checkBox = new CheckBox(label);
        checkBox.setSelected(selected);
        checkBox.selectedProperty().addListener((observable, previous, now) -> onToggled.accept(now));
        return checkBox;
    }

    private static Label hint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("now-playing-detail");
        return hint;
    }
}
