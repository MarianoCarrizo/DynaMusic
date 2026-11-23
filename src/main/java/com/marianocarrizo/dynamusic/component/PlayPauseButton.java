package com.marianocarrizo.dynamusic.component;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;

public final class PlayPauseButton extends Button {

    private static final String PLAY_TIP = "Play";
    private static final String PAUSE_TIP = "Pause";
    private static final String STYLE_CLASS = "btn-spotify-circle";

    private final Tooltip tooltip = new Tooltip();
    private final SVGPath playIcon = Icons.node(Icons.PLAY);
    private final SVGPath pauseIcon = Icons.node(Icons.PAUSE);

    public PlayPauseButton() {
        getStyleClass().add(STYLE_CLASS);
        setTooltip(tooltip);
        showPlaying(false);
    }

    public void showPlaying(boolean playing) {
        setGraphic(playing ? pauseIcon : playIcon);
        tooltip.setText(playing ? PAUSE_TIP : PLAY_TIP);
    }
}
