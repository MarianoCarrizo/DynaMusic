package com.marianocarrizo.dynamusic.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

import java.util.function.DoubleConsumer;

final class LibraryVisibility {

    static final double DEFAULT_DIVIDER = 0.3;
    private static final double MIN_DIVIDER = 0.1;
    private static final double MAX_DIVIDER = 0.9;
    private static final String HIDE_TIP = "Hide the library";
    private static final String SHOW_TIP = "Show the library";

    private final SplitPane split;
    private final Node library;
    private final ToggleButton toggle;
    private final DoubleConsumer onDividerMoved;
    private final Tooltip tooltip = new Tooltip();
    private double dividerPosition = DEFAULT_DIVIDER;
    private boolean applying = false;

    LibraryVisibility(SplitPane split, Node library, ToggleButton toggle, DoubleConsumer onDividerMoved) {
        this.split = split;
        this.library = library;
        this.toggle = toggle;
        this.onDividerMoved = onDividerMoved;
        toggle.setTooltip(tooltip);
        watchDivider();
        syncToggle();
    }

    static double sanitize(double position) {
        if (Double.isNaN(position) || position < MIN_DIVIDER || position > MAX_DIVIDER) return DEFAULT_DIVIDER;
        return position;
    }

    boolean isVisible() {
        return split.getItems().contains(library);
    }

    void setDividerPosition(double position) {
        dividerPosition = sanitize(position);
        if (isVisible()) applyDivider();
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
        watchDivider();
        applyDivider();
        Platform.runLater(this::applyDivider);
    }

    private void hide() {
        dividerPosition = sanitize(split.getDividerPositions()[0]);
        split.getItems().remove(library);
    }

    private void applyDivider() {
        applying = true;
        try {
            split.setDividerPositions(dividerPosition);
        } finally {
            applying = false;
        }
    }

    private void watchDivider() {
        if (split.getDividers().isEmpty()) return;
        split.getDividers().get(0).positionProperty().addListener((obs, oldValue, newValue) -> {
            if (applying) return;
            dividerPosition = sanitize(newValue.doubleValue());
            onDividerMoved.accept(dividerPosition);
        });
    }

    private void syncToggle() {
        toggle.setSelected(isVisible());
        tooltip.setText(isVisible() ? HIDE_TIP : SHOW_TIP);
    }
}
