package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.service.CoverThumbnails;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

final class AlbumTile extends VBox {

    private static final double SIZE = CoverThumbnails.PIXELS;
    private static final double SPACING = 4;

    private final CoverThumbnails covers;
    private final CoverBox cover = new CoverBox(SIZE);
    private final Label title = new Label();
    private final Label artist = new Label();
    private volatile Album album;

    AlbumTile(AlbumActions actions, CoverThumbnails covers) {
        super(SPACING);
        this.covers = covers;
        getStyleClass().add("album-tile");
        title.getStyleClass().add("album-tile-title");
        artist.getStyleClass().add("album-tile-artist");
        setMinWidth(SIZE);
        setPrefWidth(SIZE);
        setMaxWidth(SIZE);
        title.setMaxWidth(SIZE);
        artist.setMaxWidth(SIZE);
        getChildren().addAll(cover, title, artist);
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && album != null) actions.open(album);
        });
    }

    void bind(Album bound) {
        album = bound;
        title.setText(bound.title());
        artist.setText(bound.artist());
        AlbumCovers.show(covers, cover, bound, () -> album == bound);
    }
}
