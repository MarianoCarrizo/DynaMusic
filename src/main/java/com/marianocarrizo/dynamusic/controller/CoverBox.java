package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;

import java.util.Optional;

final class CoverBox extends StackPane {

    private static final double CAPTION_MARGIN = 12;

    private final ImageView image = new ImageView();
    private final Label caption = new Label();

    CoverBox(double size) {
        getStyleClass().add("album-cover");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        image.setFitWidth(size);
        image.setFitHeight(size);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        caption.getStyleClass().add("album-cover-caption");
        caption.setWrapText(true);
        caption.setTextAlignment(TextAlignment.CENTER);
        caption.setMaxWidth(size - CAPTION_MARGIN);
        caption.setMaxHeight(size - CAPTION_MARGIN);
        getChildren().addAll(caption, image);
    }

    void loading() {
        image.setImage(null);
        caption.setText(CoverPlaceholder.caption(false, false, null));
    }

    void resolved(Optional<Image> art, String albumTitle) {
        image.setImage(art.orElse(null));
        caption.setText(CoverPlaceholder.caption(true, art.isPresent(), albumTitle));
    }
}
