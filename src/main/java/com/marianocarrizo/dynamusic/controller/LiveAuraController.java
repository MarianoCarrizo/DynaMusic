package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.component.FilledSlider;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.model.AuraConfig;
import com.marianocarrizo.dynamusic.model.AuraPreset;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.Scene;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class LiveAuraController {

    interface Host {
        SessionConfig session();

        AuraSlot liveSlot();

        void liveAuraEdited(AuraSlot slot);
    }


    @FXML private Label lblLiveAura;
    @FXML private HBox livePresetBox;
    @FXML private FilledSlider sldLiveIntensity;
    @FXML private FilledSlider sldLiveReactivity;
    @FXML private Label lblLiveIntensity;
    @FXML private Label lblLiveReactivity;

    private final Map<AuraPreset, ToggleButton> presetButtons = new EnumMap<>(AuraPreset.class);
    private final ToggleGroup presetGroup = new ToggleGroup();
    private Host host;
    private boolean updating = false;

    void attach(Host host) {
        this.host = host;
        for (AuraPreset preset : AuraPreset.values()) {
            ToggleButton button = new ToggleButton(DisplayNames.capitalize(preset.name()));
            button.getStyleClass().addAll("btn-secondary", "btn-preset");
            button.setToggleGroup(presetGroup);
            button.setOnAction(e -> {
                if (!button.isSelected()) button.setSelected(true);
                if (!updating) pickPreset(preset);
            });
            presetButtons.put(preset, button);
            livePresetBox.getChildren().add(button);
        }
        sldLiveIntensity.valueProperty().addListener((obs, o, nv) -> {
            lblLiveIntensity.setText(Math.round(nv.doubleValue() * 100) + "%");
            if (!updating) edit(a -> a.setIntensity(nv.doubleValue()));
        });
        sldLiveReactivity.valueProperty().addListener((obs, o, nv) -> {
            lblLiveReactivity.setText(Math.round(nv.doubleValue() * 100) + "%");
            if (!updating) edit(a -> a.setReactivity(nv.doubleValue()));
        });
    }

    void refresh() {
        AuraSlot slot = host.liveSlot();
        AuraConfig aura = host.session().getActiveScene().getAura(slot);
        updating = true;
        try {
            lblLiveAura.setText("LIVE AURA · " + (slot == AuraSlot.ON_DEMAND ? "ON DEMAND" : slot.name()));
            ToggleButton button = presetButtons.get(aura.getPreset());
            if (button != null) button.setSelected(true);
            sldLiveIntensity.setValue(aura.getIntensity());
            sldLiveReactivity.setValue(aura.getReactivity());
        } finally {
            updating = false;
        }
    }

    private void pickPreset(AuraPreset preset) {
        AuraSlot slot = host.liveSlot();
        Scene scene = host.session().getActiveScene();
        AuraConfig old = scene.getAura(slot);
        if (old.getPreset() == preset) return;
        AuraConfig fresh = preset.defaults();
        fresh.setIntensity(old.getIntensity());
        fresh.setReactivity(old.getReactivity());
        scene.setAura(slot, fresh);
        host.liveAuraEdited(slot);
    }

    private void edit(Consumer<AuraConfig> change) {
        AuraSlot slot = host.liveSlot();
        change.accept(host.session().getActiveScene().getAura(slot));
        host.liveAuraEdited(slot);
    }
}
