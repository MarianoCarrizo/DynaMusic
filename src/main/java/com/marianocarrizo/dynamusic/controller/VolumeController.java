package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.IconButton;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.fxml.FXML;
import javafx.scene.control.Slider;

public final class VolumeController {

    private static final double UNMUTE_FALLBACK = 0.5;
    private static final String MUTE_TIP = "Mute";
    private static final String UNMUTE_TIP = "Unmute";

    interface Host {
        SessionConfig session();

        MusicService musicService();

        void saveSession();
    }

    @FXML private IconButton btnMute;
    @FXML private Slider sldMasterVolume;

    private Host host;
    private double volumeBeforeMute = UNMUTE_FALLBACK;

    void attach(Host host) {
        this.host = host;
        showLevel(host.session().getMasterVolume());
        sldMasterVolume.setValue(host.session().getMasterVolume());
        sldMasterVolume.valueProperty().addListener((obs, oldValue, newValue) -> {
            host.musicService().setMasterVolume(newValue.doubleValue());
            showLevel(newValue.doubleValue());
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
        showLevel(volume);
    }

    @FXML
    private void onToggleMute() {
        if (sldMasterVolume.getValue() > 0) {
            volumeBeforeMute = sldMasterVolume.getValue();
            sldMasterVolume.setValue(0);
        } else {
            sldMasterVolume.setValue(volumeBeforeMute);
        }
        persist();
    }

    private void showLevel(double volume) {
        VolumeLevel level = VolumeLevel.of(volume);
        btnMute.setIcon(level.iconPath());
        btnMute.setHint(level == VolumeLevel.MUTED ? UNMUTE_TIP : MUTE_TIP);
    }

    private void persist() {
        host.session().setMasterVolume(sldMasterVolume.getValue());
        host.saveSession();
    }
}
