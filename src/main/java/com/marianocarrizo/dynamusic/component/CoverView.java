package com.marianocarrizo.dynamusic.component;

import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

public final class CoverView extends ImageView {

    private static final double SIZE = 56;
    private static final double CORNER_DIAMETER = 12;

    public CoverView() {
        setFitWidth(SIZE);
        setFitHeight(SIZE);
        setPreserveRatio(true);
        setSmooth(true);
        Rectangle roundedClip = new Rectangle(SIZE, SIZE);
        roundedClip.setArcWidth(CORNER_DIAMETER);
        roundedClip.setArcHeight(CORNER_DIAMETER);
        setClip(roundedClip);
    }
}
