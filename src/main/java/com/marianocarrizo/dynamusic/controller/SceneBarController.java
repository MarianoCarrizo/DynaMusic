package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.model.Scene;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

public final class SceneBarController {

    interface Host {
        SessionConfig session();

        void saveSession();

        void applyActiveScene();

        void openSceneEditor();
    }


    @FXML private ComboBox<Scene> cmbScene;

    private Host host;
    private boolean refreshing = false;

    @FXML
    public void initialize() {
        cmbScene.setConverter(new StringConverter<>() {
            @Override public String toString(Scene s) { return s == null ? "" : s.getName(); }
            @Override public Scene fromString(String s) { return null; }
        });
    }

    void attach(Host host) {
        this.host = host;
        cmbScene.setOnAction(e -> {
            if (refreshing) return;
            Scene chosen = cmbScene.getValue();
            if (chosen == null || chosen.getId().equals(host.session().getActiveScene().getId())) return;
            switchScene(chosen);
        });
        refresh();
    }

    void refresh() {
        refreshing = true;
        try {
            cmbScene.setValue(null);
            cmbScene.getItems().setAll(host.session().getScenes());
            cmbScene.setValue(host.session().getActiveScene());
        } finally {
            refreshing = false;
        }
    }

    private void switchScene(Scene target) {
        host.session().setActiveScene(target.getId());
        host.saveSession();
        refresh();
        host.applyActiveScene();
    }

    @FXML
    private void onNewScene() {
        NamePrompt.ask("New scene", "Name:", "New scene").ifPresent(name ->
            switchScene(host.session().addScene(name)));
    }

    @FXML
    private void onSaveSceneAs() {
        Scene active = host.session().getActiveScene();
        NamePrompt.ask("Save as new scene", "Name:", active.getName() + " copy").ifPresent(name -> {
            Scene copy = host.session().duplicateScene(active.getId(), name);
            if (copy != null) switchScene(copy);
        });
    }

    @FXML
    private void onEditScene() {
        host.openSceneEditor();
    }
}
