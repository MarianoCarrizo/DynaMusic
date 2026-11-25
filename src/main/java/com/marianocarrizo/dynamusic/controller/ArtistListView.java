package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Artist;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;

import java.util.function.Consumer;

final class ArtistListView extends ListView<Artist> {

    ArtistListView(Consumer<Artist> onOpen) {
        getStyleClass().add("artist-list");
        setCellFactory(list -> new ArtistCell(onOpen));
        setOnKeyPressed(event -> {
            Artist selected = getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.ENTER && selected != null) onOpen.accept(selected);
        });
    }

    private static final class ArtistCell extends ListCell<Artist> {

        ArtistCell(Consumer<Artist> onOpen) {
            setOnMouseClicked(event -> {
                if (!isEmpty() && getItem() != null) onOpen.accept(getItem());
            });
        }

        @Override
        protected void updateItem(Artist item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            int albums = item.albums().size();
            setText(item.name() + "  ·  " + albums + (albums == 1 ? " album" : " albums"));
        }
    }
}
