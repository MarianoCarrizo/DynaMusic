package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;

public final class OnDemandPanelController {

    interface Host {
        MusicService musicService();

        void closeOnDemand();
    }

    @FXML private HBox onDemandPanel;
    @FXML private ToggleButton btnOnDemandLoop;

    private Host host;

    void attach(Host host) {
        this.host = host;
        show(false);
    }

    void show(boolean show) {
        onDemandPanel.setVisible(show);
        onDemandPanel.setManaged(show);
    }

    @FXML
    private void onOnDemandClose() {
        host.closeOnDemand();
    }

    @FXML
    private void onToggleOnDemandLoop() {
        host.musicService().setOnDemandLoop(btnOnDemandLoop.isSelected());
    }
}
