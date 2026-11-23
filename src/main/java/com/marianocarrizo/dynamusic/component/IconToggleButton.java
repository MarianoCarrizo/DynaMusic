package com.marianocarrizo.dynamusic.component;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public final class IconToggleButton extends ToggleButton {

    private static final String STYLE_CLASS = "btn-icon-toggle";
    private static final String DOT_STYLE_CLASS = "icon-dot";
    private static final double DOT_RADIUS = 2;
    private static final double DOT_GAP = 2;

    private final VBox content = new VBox(DOT_GAP);
    private final Circle dot = new Circle(DOT_RADIUS);

    public IconToggleButton() {
        getStyleClass().add(STYLE_CLASS);
        dot.getStyleClass().add(DOT_STYLE_CLASS);
        dot.opacityProperty().bind(Bindings.when(selectedProperty()).then(1.0).otherwise(0.0));
        content.setAlignment(Pos.CENTER);
        setGraphic(content);
    }

    public void setIcon(String iconPath) {
        content.getChildren().setAll(Icons.node(iconPath), dot);
    }

    public void setHint(String text) {
        setTooltip(new Tooltip(text));
    }
}
