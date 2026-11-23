package com.marianocarrizo.dynamusic.component;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

public final class IconButton extends Button {

    private static final String STYLE_CLASS = "btn-icon";

    public IconButton() {
        getStyleClass().add(STYLE_CLASS);
    }

    public void setIcon(String iconPath) {
        setGraphic(Icons.node(iconPath));
    }

    public void setHint(String text) {
        setTooltip(new Tooltip(text));
    }
}
