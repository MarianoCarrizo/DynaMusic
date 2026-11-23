package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.BackgroundConfig;
import com.marianocarrizo.dynamusic.model.BackgroundTarget;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.FileChooser;

import java.io.File;

final class MoodBackgroundMenu {

    private final PlayerHost host;

    MoodBackgroundMenu(PlayerHost host) {
        this.host = host;
    }

    ContextMenu forSlot(AuraSlot slot) {
        MenuItem choose = new MenuItem("Choose...");
        choose.setOnAction(e -> chooseFor(slot));
        MenuItem useScene = new MenuItem("Use scene background");
        useScene.setOnAction(e -> useSceneBackgroundFor(slot));
        ContextMenu menu = new ContextMenu(new Menu("Background", null, choose, useScene));
        menu.setOnShowing(e -> useScene.setDisable(!host.session().getActiveScene().hasOwnBackground(slot)));
        return menu;
    }

    private void chooseFor(AuraSlot slot) {
        FileChooser chooser = BackgroundEditorController.newBackgroundChooser(host.session().getLastImageFolderPath());
        File file = chooser.showOpenDialog(host.window());
        if (file == null) return;
        BackgroundConfig cfg = BackgroundTarget.of(slot).getOrCreateIn(host.session().getActiveScene());
        cfg.setPath(file.getAbsolutePath());
        cfg.setSegmentStartMs(null);
        cfg.setSegmentEndMs(null);
        cfg.setPlaybackRate(1.0);
        cfg.setBoomerangEnabled(false);
        host.session().setLastImageFolderPath(file.getParent());
        host.slotBackgroundEdited();
    }

    private void useSceneBackgroundFor(AuraSlot slot) {
        host.session().getActiveScene().clearBackground(slot);
        host.slotBackgroundEdited();
    }
}
