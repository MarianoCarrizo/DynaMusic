package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.library.TrackEntry;
import com.marianocarrizo.dynamusic.service.CoverThumbnails;
import com.marianocarrizo.dynamusic.service.TrackLabels;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.Optional;

final class AlbumDetailView extends VBox {

    private static final double COVER_SIZE = 96;
    private static final double SPACING = 10;

    private final AlbumActions actions;
    private final CoverThumbnails covers;
    private final ImageView cover = new ImageView();
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final ListView<TrackEntry> trackList = new ListView<>();
    private volatile Album album;

    AlbumDetailView(AlbumActions actions, FileTrackMenuItems.Host menuHost, CoverThumbnails covers) {
        super(SPACING);
        this.actions = actions;
        this.covers = covers;
        getStyleClass().add("album-detail");
        cover.setFitWidth(COVER_SIZE);
        cover.setFitHeight(COVER_SIZE);
        cover.setPreserveRatio(true);
        cover.setSmooth(true);
        title.getStyleClass().add("album-detail-title");
        subtitle.getStyleClass().add("album-tile-artist");
        trackList.setCellFactory(list -> new TrackCell(menuHost));
        trackList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) playSelected();
        });
        VBox.setVgrow(trackList, Priority.ALWAYS);
        getChildren().addAll(header(), trackList);
    }

    void show(Album shown) {
        album = shown;
        title.setText(shown.title());
        subtitle.setText(subtitleOf(shown));
        trackList.getItems().setAll(shown.tracks());
        AlbumCovers.show(covers, cover, shown, () -> album == shown);
    }

    private HBox header() {
        Button play = new Button("Play album");
        play.getStyleClass().add("btn-primary");
        play.setOnAction(e -> actions.play(album, 0));
        Button playNext = new Button("Play next");
        playNext.getStyleClass().add("btn-secondary");
        playNext.setOnAction(e -> actions.enqueue(album, QueuePlacement.NEXT));
        Button addToQueue = new Button("Add to queue");
        addToQueue.getStyleClass().add("btn-secondary");
        addToQueue.setOnAction(e -> actions.enqueue(album, QueuePlacement.END));
        HBox buttons = new HBox(SPACING, play, playNext, addToQueue);
        VBox text = new VBox(4, title, subtitle, buttons);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(12, cover, text);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void playSelected() {
        int index = trackList.getSelectionModel().getSelectedIndex();
        if (index >= 0) actions.play(album, index);
    }

    private static String subtitleOf(Album shown) {
        int count = shown.tracks().size();
        String tracks = count + (count == 1 ? " track" : " tracks");
        String year = shown.year().isEmpty() ? "" : "  ·  " + shown.year();
        return shown.artist() + year + "  ·  " + tracks;
    }

    private final class TrackCell extends ListCell<TrackEntry> {

        private final ContextMenu menu = new ContextMenu();

        TrackCell(FileTrackMenuItems.Host menuHost) {
            menu.getItems().addAll(FileTrackMenuItems.create(menuHost, this::file, () -> null));
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !isEmpty()) playSelected();
            });
        }

        @Override
        protected void updateItem(TrackEntry item, boolean empty) {
            super.updateItem(item, empty);
            UnplayableStyle.apply(this, empty || item == null ? null : file());
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            String trackTitle = TrackLabels.of(file(), Optional.of(item.tags())).title();
            boolean sameArtist = item.tags().artist().isEmpty() || item.tags().artist().equalsIgnoreCase(album.artist());
            String artist = sameArtist ? "" : "  ·  " + item.tags().artist();
            setText((getIndex() + 1) + "   " + trackTitle + artist);
            setContextMenu(menu);
        }

        private File file() {
            return getItem() == null ? null : new File(getItem().path());
        }
    }
}
