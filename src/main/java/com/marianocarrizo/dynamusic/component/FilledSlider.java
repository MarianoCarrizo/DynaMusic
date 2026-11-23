package com.marianocarrizo.dynamusic.component;

import javafx.scene.control.Skin;
import javafx.scene.control.Slider;

public class FilledSlider extends Slider {

    private static final String STYLE_CLASS = "filled-slider";

    public FilledSlider() {
        getStyleClass().add(STYLE_CLASS);
    }

    public FilledSlider(double min, double max, double value) {
        super(min, max, value);
        getStyleClass().add(STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new FilledSliderSkin(this);
    }
}
