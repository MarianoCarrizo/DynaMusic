package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.PlayQueue;
import com.marianocarrizo.dynamusic.service.TrackLabels;
import com.marianocarrizo.dynamusic.tags.TrackTagStore;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;

import java.io.File;
import java.util.List;

public final class QueuePanelController {

    interface Host {
        PlayQueue queue();

        TrackTagStore tags();

        void next();
    }

    private static final String EMPTY_HINT = "Nothing queued. Use Play next or Add to queue on a track.";

    @FXML private ListView<File> lstQueue;
    @FXML private Label lblQueueCount;

    private Host host;

    void attach(Host host) {
        this.host = host;
        Label hint = new Label(EMPTY_HINT);
        hint.getStyleClass().add("empty-state-hint");
        lstQueue.setPlaceholder(hint);
        lstQueue.setCellFactory(list -> new QueueCell(cellHost()));
        lstQueue.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) playSelected();
        });
        lstQueue.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) removeAt(lstQueue.getSelectionModel().getSelectedIndex());
        });
        host.queue().addListener(this::refresh);
        host.tags().onLoaded(lstQueue::refresh);
        refresh();
    }

    @FXML
    private void onClear() {
        host.queue().clear();
    }

    private void refresh() {
        List<File> items = host.queue().items();
        host.tags().request(List.copyOf(items));
        lstQueue.getItems().setAll(items);
        lblQueueCount.setText(items.isEmpty() ? "" : String.valueOf(items.size()));
    }

    private void playSelected() {
        int index = lstQueue.getSelectionModel().getSelectedIndex();
        if (index < 0) return;
        host.queue().moveToFront(index);
        host.next();
    }

    private void removeAt(int index) {
        host.queue().remove(index);
        int last = lstQueue.getItems().size() - 1;
        if (last >= 0) lstQueue.getSelectionModel().select(Math.min(index, last));
    }

    private QueueCell.Host cellHost() {
        return new QueueCell.Host() {
            @Override public TrackLabels labelsOf(File file) {
                return TrackLabels.of(file, host.tags().peek(file));
            }
            @Override public void moved(int from, int slot) {
                host.queue().move(from, QueueReorder.targetIndex(from, slot));
            }
            @Override public void removed(int index) {
                removeAt(index);
            }
        };
    }
}
