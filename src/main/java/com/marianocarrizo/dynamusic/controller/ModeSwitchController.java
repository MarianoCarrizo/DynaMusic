package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

public final class ModeSwitchController {

    interface Host {
        ReadOnlyObjectProperty<AppMode> modeProperty();

        void request(AppMode mode);
    }

    @FXML private ToggleGroup modeGroup;
    @FXML private ToggleButton listenButton;
    @FXML private ToggleButton sessionButton;

    private Host host;

    void attach(Host host) {
        this.host = host;
        listenButton.setUserData(AppMode.LISTEN);
        sessionButton.setUserData(AppMode.SESSION);
        select(host.modeProperty().get());
        modeGroup.selectedToggleProperty().addListener((obs, previous, chosen) -> onChosen(previous, chosen));
        host.modeProperty().addListener((obs, previous, mode) -> select(mode));
    }

    private void onChosen(Toggle previous, Toggle chosen) {
        if (chosen == null) {
            modeGroup.selectToggle(previous);
            return;
        }
        host.request((AppMode) chosen.getUserData());
    }

    private void select(AppMode mode) {
        modeGroup.selectToggle(mode == AppMode.LISTEN ? listenButton : sessionButton);
    }
}
