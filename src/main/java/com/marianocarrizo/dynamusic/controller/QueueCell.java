package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.TrackLabels;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;

final class QueueCell extends ListCell<File> {

    interface Host {
        TrackLabels labelsOf(File file);

        void moved(int from, int slot);

        void removed(int index);
    }

    private static final DataFormat QUEUE_ROW = new DataFormat("application/x-dynamusic-queue-row");
    private static final PseudoClass DROP_ABOVE = PseudoClass.getPseudoClass("drop-above");
    private static final PseudoClass DROP_BELOW = PseudoClass.getPseudoClass("drop-below");
    private static final double POSITION_WIDTH = 22;

    private final Host host;
    private final ContextMenu menu = new ContextMenu();
    private final Label position = new Label();
    private final Label title = new Label();
    private final Label detail = new Label();
    private final HBox row = new HBox(8);

    QueueCell(Host host) {
        this.host = host;
        MenuItem remove = new MenuItem("Remove from queue");
        remove.setOnAction(e -> host.removed(getIndex()));
        menu.getItems().add(remove);
        buildRow();
        setOnDragDetected(this::startDrag);
        setOnDragOver(this::acceptDrag);
        setOnDragExited(e -> showIndicator(false, false));
        setOnDragDropped(this::handleDrop);
    }

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        UnplayableStyle.apply(this, empty ? null : item);
        if (empty || item == null) {
            setGraphic(null);
            setContextMenu(null);
            return;
        }
        TrackLabels labels = host.labelsOf(item);
        position.setText(String.valueOf(getIndex() + 1));
        title.setText(labels.title());
        detail.setText(labels.detail());
        detail.setVisible(!labels.detail().isEmpty());
        detail.setManaged(!labels.detail().isEmpty());
        setGraphic(row);
        setContextMenu(menu);
    }

    private void buildRow() {
        position.getStyleClass().add("playlist-row-marker");
        position.setMinWidth(POSITION_WIDTH);
        title.getStyleClass().add("playlist-row-title");
        detail.getStyleClass().add("playlist-row-album");
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(position, new VBox(1, title, detail));
    }

    private void startDrag(MouseEvent event) {
        if (isEmpty()) return;
        Dragboard board = startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.put(QUEUE_ROW, getIndex());
        board.setContent(content);
        event.consume();
    }

    private void acceptDrag(DragEvent event) {
        if (!event.getDragboard().hasContent(QUEUE_ROW)) return;
        event.acceptTransferModes(TransferMode.MOVE);
        boolean above = dropSlot(event) == getIndex();
        showIndicator(!isEmpty() && above, !isEmpty() && !above);
        event.consume();
    }

    private void handleDrop(DragEvent event) {
        Dragboard board = event.getDragboard();
        boolean handled = board.hasContent(QUEUE_ROW);
        if (handled) host.moved((Integer) board.getContent(QUEUE_ROW), dropSlot(event));
        showIndicator(false, false);
        event.setDropCompleted(handled);
        event.consume();
    }

    private int dropSlot(DragEvent event) {
        if (isEmpty()) return getListView().getItems().size();
        return event.getY() > getHeight() / 2 ? getIndex() + 1 : getIndex();
    }

    private void showIndicator(boolean above, boolean below) {
        pseudoClassStateChanged(DROP_ABOVE, above);
        pseudoClassStateChanged(DROP_BELOW, below);
    }
}
