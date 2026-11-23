package com.marianocarrizo.dynamusic.component;

import javafx.scene.control.Slider;
import javafx.scene.control.skin.SliderSkin;
import javafx.scene.layout.Region;
import javafx.geometry.Orientation;

final class FilledSliderSkin extends SliderSkin {

    private static final String FILL_STYLE_CLASS = "fill";
    private static final String TRACK_SELECTOR = ".track";
    private static final String THUMB_SELECTOR = ".thumb";

    private final Region fill = new Region();

    FilledSliderSkin(Slider slider) {
        super(slider);
        fill.getStyleClass().add(FILL_STYLE_CLASS);
        fill.setMouseTransparent(true);
        getChildren().add(indexBelowThumb(), fill);
        slider.valueProperty().addListener(o -> slider.requestLayout());
        slider.minProperty().addListener(o -> slider.requestLayout());
        slider.maxProperty().addListener(o -> slider.requestLayout());
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        Region track = (Region) getSkinnable().lookup(TRACK_SELECTOR);
        Region thumb = (Region) getSkinnable().lookup(THUMB_SELECTOR);
        if (track == null || thumb == null) {
            return;
        }
        if (getSkinnable().getOrientation() == Orientation.HORIZONTAL) {
            FillSpan span = FillSpan.horizontal(track.getLayoutX(), track.getWidth(),
                    thumb.getLayoutX() + thumb.getWidth() / 2);
            fill.resizeRelocate(span.start(), track.getLayoutY(), span.length(), track.getHeight());
        } else {
            FillSpan span = FillSpan.vertical(track.getLayoutY(), track.getHeight(),
                    thumb.getLayoutY() + thumb.getHeight() / 2);
            fill.resizeRelocate(track.getLayoutX(), span.start(), track.getWidth(), span.length());
        }
    }

    private int indexBelowThumb() {
        Region thumb = (Region) getSkinnable().lookup(THUMB_SELECTOR);
        int index = getChildren().indexOf(thumb);
        return index < 0 ? getChildren().size() : index;
    }
}
