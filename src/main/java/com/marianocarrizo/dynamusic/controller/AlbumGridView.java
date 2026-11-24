package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.service.CoverThumbnails;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;

final class AlbumGridView extends ListView<List<Album>> {

    private static final double TILE_WIDTH = CoverThumbnails.PIXELS;
    private static final double GAP = 10;
    private static final double ROW_HEIGHT = 204;
    private static final double SCROLLBAR_ALLOWANCE = 28;

    private List<Album> albums = List.of();
    private int columns = 1;

    AlbumGridView(AlbumActions actions, CoverThumbnails covers) {
        getStyleClass().add("album-grid");
        setFixedCellSize(ROW_HEIGHT);
        setFocusTraversable(false);
        setCellFactory(list -> new RowCell(actions, covers));
        widthProperty().addListener((obs, previous, width) -> resize(width.doubleValue()));
    }

    void setAlbums(List<Album> shown) {
        albums = shown;
        rebuild();
    }

    private void resize(double width) {
        int fitting = AlbumGridRows.columnsFor(width - SCROLLBAR_ALLOWANCE, TILE_WIDTH, GAP);
        if (fitting == columns) return;
        columns = fitting;
        rebuild();
    }

    private void rebuild() {
        getItems().setAll(AlbumGridRows.of(albums, columns));
    }

    private static final class RowCell extends ListCell<List<Album>> {

        private final AlbumActions actions;
        private final CoverThumbnails covers;
        private final HBox row = new HBox(GAP);
        private final List<AlbumTile> tiles = new ArrayList<>();

        RowCell(AlbumActions actions, CoverThumbnails covers) {
            this.actions = actions;
            this.covers = covers;
        }

        @Override
        protected void updateItem(List<Album> item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            while (tiles.size() < item.size()) tiles.add(new AlbumTile(actions, covers));
            for (int i = 0; i < item.size(); i++) tiles.get(i).bind(item.get(i));
            row.getChildren().setAll(tiles.subList(0, item.size()));
            setGraphic(row);
        }
    }
}
