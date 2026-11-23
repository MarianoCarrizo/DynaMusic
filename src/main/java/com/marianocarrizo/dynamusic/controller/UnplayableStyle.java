package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.audio.PlaybackSupport;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

import java.io.File;

final class UnplayableStyle {

    static final String STYLE_CLASS = "track-unplayable";

    private static final Tooltip TOOLTIP = new Tooltip(PlaybackSupport.FFMPEG_MISSING_MESSAGE);

    private UnplayableStyle() {}

    static void apply(Control cell, File file) {
        cell.getStyleClass().remove(STYLE_CLASS);
        cell.setTooltip(null);
        if (file == null || !PlaybackSupport.isBlockedByMissingFfmpeg(file)) return;
        cell.getStyleClass().add(STYLE_CLASS);
        cell.setTooltip(TOOLTIP);
    }
}
