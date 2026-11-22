package com.marianocarrizo.dynamusic.controller;

import javafx.css.PseudoClass;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.List;

final class PlaylistTrackCell extends ListCell<String> {

    interface Host {
        boolean isNowPlaying(int index);
        void filesDropped(List<File> files, int slot);
        void trackMoved(int from, int slot);
        void removeRequested(int index);
    }

    private static final DataFormat TRACK_ROW = new DataFormat("application/x-dynamusic-playlist-row");
    private static final PseudoClass DROP_ABOVE = PseudoClass.getPseudoClass("drop-above");
    private static final PseudoClass DROP_BELOW = PseudoClass.getPseudoClass("drop-below");
    private static final String NOW_PLAYING_STYLE = "list-cell-now-playing";

    private final Host host;
    private final ContextMenu menu = new ContextMenu();

    PlaylistTrackCell(Host host) {
        this.host = host;
        MenuItem remove = new MenuItem("Remove from playlist");
        remove.setOnAction(e -> host.removeRequested(getIndex()));
        menu.getItems().add(remove);

        setOnDragDetected(this::startRowDrag);
        setOnDragOver(this::acceptDrag);
        setOnDragExited(e -> showIndicator(false, false));
        setOnDragDropped(this::handleDrop);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().remove(NOW_PLAYING_STYLE);
        if (empty || item == null) {
            setText(null);
            setContextMenu(null);
            return;
        }
        setText(item);
        setContextMenu(menu);
        if (host.isNowPlaying(getIndex())) getStyleClass().add(NOW_PLAYING_STYLE);
    }

    static void acceptFileDropsOnList(ListView<String> list, Host host) {
        list.setOnDragOver(event -> {
            if (!event.getDragboard().hasFiles()) return;
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        list.setOnDragDropped(event -> {
            if (!event.getDragboard().hasFiles()) return;
            host.filesDropped(event.getDragboard().getFiles(), list.getItems().size());
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private void startRowDrag(MouseEvent event) {
        if (isEmpty()) return;
        Dragboard board = startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.put(TRACK_ROW, getIndex());
        board.setContent(content);
        event.consume();
    }

    private void acceptDrag(DragEvent event) {
        Dragboard board = event.getDragboard();
        if (board.hasContent(TRACK_ROW)) {
            event.acceptTransferModes(TransferMode.MOVE);
        } else if (board.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        } else {
            return;
        }
        if (isEmpty()) {
            showIndicator(isFirstEmptyRow(), false);
        } else {
            boolean above = dropSlot(event) == getIndex();
            showIndicator(above, !above);
        }
        event.consume();
    }

    private void handleDrop(DragEvent event) {
        Dragboard board = event.getDragboard();
        int slot = dropSlot(event);
        boolean handled = false;
        if (board.hasContent(TRACK_ROW)) {
            host.trackMoved((Integer) board.getContent(TRACK_ROW), slot);
            handled = true;
        } else if (board.hasFiles()) {
            host.filesDropped(board.getFiles(), slot);
            handled = true;
        }
        showIndicator(false, false);
        event.setDropCompleted(handled);
        event.consume();
    }

    private int dropSlot(DragEvent event) {
        if (isEmpty()) return getListView().getItems().size();
        boolean lowerHalf = event.getY() > getHeight() / 2;
        return lowerHalf ? getIndex() + 1 : getIndex();
    }

    private boolean isFirstEmptyRow() {
        return isEmpty() && getIndex() == getListView().getItems().size();
    }

    private void showIndicator(boolean above, boolean below) {
        pseudoClassStateChanged(DROP_ABOVE, above);
        pseudoClassStateChanged(DROP_BELOW, below);
    }
}
