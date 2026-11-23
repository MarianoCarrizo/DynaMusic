package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.UpdateManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.concurrent.CompletableFuture;

public final class UpdateBarController {

    interface Host {
        void shutdownApp();
    }


    @FXML private HBox updateBar;
    @FXML private Label lblUpdate;
    @FXML private Button btnUpdateNow;
    @FXML private Button btnUpdateOnClose;

    private Host host;
    private UpdateManager.ReadyUpdate readyUpdate;

    void attach(Host host) {
        this.host = host;
        UpdateManager.get().checkAsync(update -> Platform.runLater(() -> showUpdateNotice(update)));
    }

    private void showUpdateNotice(UpdateManager.ReadyUpdate update) {
        if (readyUpdate != null && !update.version().isNewerThan(readyUpdate.version())) return;
        readyUpdate = update;
        lblUpdate.setText("Version " + update.version() + " is ready");
        btnUpdateNow.setDisable(false);
        showOnCloseButton(true);
        updateBar.setVisible(true);
        updateBar.setManaged(true);
    }

    private void showOnCloseButton(boolean show) {
        btnUpdateOnClose.setVisible(show);
        btnUpdateOnClose.setManaged(show);
    }

    @FXML
    private void onInstallUpdateNow() {
        UpdateManager.ReadyUpdate update = readyUpdate;
        if (update == null) return;
        btnUpdateNow.setDisable(true);
        CompletableFuture.supplyAsync(() -> UpdateManager.get().verify(update)).thenAccept(ok -> Platform.runLater(() -> {
            if (!ok) {
                lblUpdate.setText("The downloaded update is damaged. It will be downloaded again on the next start.");
                showOnCloseButton(false);
                return;
            }
            UpdateManager.get().installOnClose(update);
            if (!UpdateManager.get().launchPendingInstaller()) {
                lblUpdate.setText("The installer could not be started. Try again or install it on close.");
                btnUpdateNow.setDisable(false);
                return;
            }
            host.shutdownApp();
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                lblUpdate.setText("The update could not be checked: " + ex.getMessage());
                btnUpdateNow.setDisable(false);
            });
            return null;
        });
    }

    @FXML
    private void onInstallUpdateOnClose() {
        UpdateManager.ReadyUpdate update = readyUpdate;
        if (update == null) return;
        UpdateManager.get().installOnClose(update);
        lblUpdate.setText("Version " + update.version() + " will be installed when you close the app");
        showOnCloseButton(false);
    }
}
