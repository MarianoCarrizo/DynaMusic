package com.marianocarrizo.dynamusic.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

final class LibraryVisibility {

    private static final double DEFAULT_DIVIDER = 0.3;
    private static final String HIDE_TIP = "Hide the library";
    private static final String SHOW_TIP = "Show the library";

    private final SplitPane split;
    private final Node library;
    private final ToggleButton toggle;
    private final Tooltip tooltip = new Tooltip();
    private double dividerPosition = DEFAULT_DIVIDER;

    LibraryVisibility(SplitPane split, Node library, ToggleButton toggle) {
        this.split = split;
        this.library = library;
        this.toggle = toggle;
        toggle.setTooltip(tooltip);
        syncToggle();
    }

    boolean isVisible() {
        return split.getItems().contains(library);
    }

    void set(boolean visible) {
        if (visible == isVisible()) {
            syncToggle();
            return;
        }
        if (visible) show();
        else hide();
        syncToggle();
    }

    private void show() {
        split.getItems().add(0, library);
        Platform.runLater(() -> split.setDividerPositions(dividerPosition));
    }

    private void hide() {
        dividerPosition = split.getDividerPositions()[0];
        split.getItems().remove(library);
    }

    private void syncToggle() {
        toggle.setSelected(isVisible());
        tooltip.setText(isVisible() ? HIDE_TIP : SHOW_TIP);
    }
}
