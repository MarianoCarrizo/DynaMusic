package com.marianocarrizo.dynamusic.component;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

public final class PlayPauseButton extends Button {

    private static final String PLAY_ICON = "▶";
    private static final String PAUSE_ICON = "⏸";
    private static final String PLAY_TIP = "Play";
    private static final String PAUSE_TIP = "Pause";
    private static final String STYLE_CLASS = "btn-spotify-circle";

    private final Tooltip tooltip = new Tooltip();

    public PlayPauseButton() {
        getStyleClass().add(STYLE_CLASS);
        setTooltip(tooltip);
        showPlaying(false);
    }

    public void showPlaying(boolean playing) {
        setText(playing ? PAUSE_ICON : PLAY_ICON);
        tooltip.setText(playing ? PAUSE_TIP : PLAY_TIP);
    }
}
