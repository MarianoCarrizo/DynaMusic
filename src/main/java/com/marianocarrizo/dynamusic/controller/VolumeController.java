package com.marianocarrizo.dynamusic.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;

public final class VolumeController {

    @FXML private Slider sldMasterVolume;
    @FXML private Label lblMasterVolume;

    private PlayerHost host;

    void attach(PlayerHost host) {
        this.host = host;
        showPercent(host.session().getMasterVolume());
        sldMasterVolume.setValue(host.session().getMasterVolume());
        sldMasterVolume.valueProperty().addListener((obs, oldValue, newValue) -> {
            host.musicService().setMasterVolume(newValue.doubleValue());
            showPercent(newValue.doubleValue());
        });
        sldMasterVolume.setOnMouseReleased(e -> persist());
        sldMasterVolume.valueChangingProperty().addListener((o, was, is) -> {
            if (was && !is) persist();
        });
        sldMasterVolume.focusedProperty().addListener((o, had, has) -> {
            if (had && !has) persist();
        });
    }

    void restore(double volume) {
        host.session().setMasterVolume(volume);
        host.musicService().setMasterVolume(volume);
        sldMasterVolume.setValue(volume);
        showPercent(volume);
    }

    private void showPercent(double volume) {
        lblMasterVolume.setText(Math.round(volume * 100) + "%");
    }

    private void persist() {
        host.session().setMasterVolume(sldMasterVolume.getValue());
        host.saveSession();
    }
}
