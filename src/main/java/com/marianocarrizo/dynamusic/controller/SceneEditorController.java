package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.BackgroundTarget;
import com.marianocarrizo.dynamusic.model.Scene;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public class SceneEditorController {

    @FXML private TextField txtSceneName;
    @FXML private Button btnDeleteScene;
    @FXML private TabPane tabPane;
    @FXML private Tab tabBackground;
    @FXML private BackgroundEditorController backgroundEditorController;

    private SceneHost host;
    private Stage stage;
    private final List<MoodTabContent> moodTabs = new ArrayList<>();
    private final List<BackgroundEditorController> backgroundEditors = new ArrayList<>();
    private boolean revertingTab = false;

    static SceneEditorController create(Window owner, SceneHost host) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneEditorController.class.getResource("/views/scene_editor.fxml"));
            Parent root = loader.load();
            SceneEditorController controller = loader.getController();
            controller.init(root, owner, host);
            return controller;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load scene editor", e);
        }
    }

    private void init(Parent root, Window owner, SceneHost host) {
        this.host = host;

        int index = 0;
        for (AuraSlot slot : new AuraSlot[]{AuraSlot.PAUSED, AuraSlot.NORMAL, AuraSlot.RAGED,
                AuraSlot.ADVANTAGE, AuraSlot.ON_DEMAND}) {
            MoodTabContent content = new MoodTabContent(slot, host);
            moodTabs.add(content);
            backgroundEditors.add(content.backgroundEditor());
            ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("editor-scroll");
            Tab tab = new Tab(tabTitle(slot), scroll);
            tab.setClosable(false);
            tab.setUserData(content.backgroundEditor());
            tabPane.getTabs().add(index++, tab);
        }
        tabBackground.setUserData(backgroundEditorController);
        backgroundEditors.add(backgroundEditorController);
        tabPane.getSelectionModel().select(0);
        tabPane.getSelectionModel().selectedItemProperty().addListener((o, previous, current) -> onTabChanged(previous));

        txtSceneName.setOnAction(e -> commitName());
        txtSceneName.focusedProperty().addListener((o, had, has) -> {
            if (had && !has) commitName();
        });

        backgroundEditorController.init(host, BackgroundTarget.SCENE_DEFAULT);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 620, 700);
        scene.setFill(Color.web("#1e1e24"));
        var css = SceneEditorController.class.getResource("/styles/dark-theme.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            if (owner instanceof Stage s) stage.getIcons().setAll(s.getIcons());
        }
        stage.setScene(scene);
        stage.setMinWidth(520);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(e -> {
            if (!confirmDiscardAll()) e.consume();
        });
        stage.setOnHidden(e -> backgroundEditors.forEach(BackgroundEditorController::dispose));
    }

    void show() {
        if (stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
            return;
        }
        refresh();
        stage.show();
        tabPane.requestFocus();
    }

    void dispose() {
        backgroundEditors.forEach(BackgroundEditorController::dispose);
        if (stage != null) stage.hide();
    }

    void refresh() {
        Scene scene = host.session().getActiveScene();
        stage.setTitle("Edit scene - " + scene.getName());
        txtSceneName.setText(scene.getName());
        btnDeleteScene.setDisable(host.session().getScenes().size() <= 1);
        for (MoodTabContent tab : moodTabs) tab.refresh(scene);
        backgroundEditors.forEach(BackgroundEditorController::refresh);
    }

    void refreshAuras() {
        Scene scene = host.session().getActiveScene();
        for (MoodTabContent tab : moodTabs) tab.refresh(scene);
    }

    void refreshTracks() {
        Scene scene = host.session().getActiveScene();
        for (MoodTabContent tab : moodTabs) tab.refreshTrack(scene);
    }

    void refreshBackground() {
        backgroundEditors.forEach(BackgroundEditorController::refreshLabel);
    }

    private void onTabChanged(Tab previous) {
        if (revertingTab) return;
        BackgroundEditorController leaving = (BackgroundEditorController) previous.getUserData();
        if (leaving.hasUnappliedChanges() && !confirmDiscard(leaving)) {
            revertingTab = true;
            try {
                tabPane.getSelectionModel().select(previous);
            } finally {
                revertingTab = false;
            }
            return;
        }
        leaving.discardChanges();
    }

    private boolean confirmDiscardAll() {
        for (BackgroundEditorController editor : backgroundEditors) {
            if (editor.hasUnappliedChanges() && !confirmDiscard(editor)) return false;
        }
        return true;
    }

    private boolean confirmDiscard(BackgroundEditorController editor) {
        return ThemedDialog.confirm(
                stage,
                "Unapplied changes",
                "You have unapplied background changes for " + editor.targetLabel() + ". Discard them?",
                "Discard",
                "Cancel");
    }

    private void commitName() {
        Scene scene = host.session().getActiveScene();
        String name = txtSceneName.getText() == null ? "" : txtSceneName.getText().trim();
        if (name.isEmpty() || name.equals(scene.getName())) {
            txtSceneName.setText(scene.getName());
            return;
        }
        host.session().renameScene(scene.getId(), name);
        host.saveSession();
        host.onSceneRenamed();
        stage.setTitle("Edit scene - " + name);
    }

    @FXML
    private void onDeleteScene() {
        Scene scene = host.session().getActiveScene();
        if (host.session().getScenes().size() <= 1) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete scene \"" + scene.getName() + "\"? This cannot be undone.");
        confirm.initOwner(stage);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        host.deleteActiveScene();
    }

    private static String tabTitle(AuraSlot slot) {
        return switch (slot) {
            case PAUSED    -> "Paused";
            case NORMAL    -> "Normal";
            case RAGED     -> "Raged";
            case ADVANTAGE -> "Advantage";
            case ON_DEMAND -> "On demand";
        };
    }
}
