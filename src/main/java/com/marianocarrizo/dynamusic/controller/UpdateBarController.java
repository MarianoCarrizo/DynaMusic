package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.UpdateChecker;
import com.marianocarrizo.dynamusic.service.UpdateChoice;
import com.marianocarrizo.dynamusic.service.UpdateManager;
import com.marianocarrizo.dynamusic.service.UpdateOffer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.concurrent.CompletableFuture;

public final class UpdateBarController {

    interface Host {
        void shutdownApp();

        SessionConfig session();

        void saveSession();
    }


    @FXML private HBox updateBar;
    @FXML private Label lblUpdate;
    @FXML private Button btnUpdateNow;
    @FXML private Button btnUpdateOnClose;

    private Host host;
    private UpdateManager.ReadyUpdate readyUpdate;

    void attach(Host host) {
        this.host = host;
        UpdateManager.get().checkAsync(this::isOffered, update -> Platform.runLater(() -> showUpdateNotice(update)));
    }

    private boolean isOffered(UpdateChecker.Version version) {
        return UpdateOffer.isOffered(version, UpdateOffer.skipped(host.session().getSkippedUpdateVersion()));
    }

    private void showUpdateNotice(UpdateManager.ReadyUpdate update) {
        if (!isOffered(update.version())) return;
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
            UpdateManager.get().choose(UpdateChoice.INSTALL_NOW, update);
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
        UpdateManager.get().choose(UpdateChoice.INSTALL_ON_CLOSE, update);
        lblUpdate.setText("Version " + update.version() + " will be installed when you close the app");
        showOnCloseButton(false);
    }

    @FXML
    private void onUpdateNotNow() {
        decline(UpdateChoice.NOT_NOW);
    }

    @FXML
    private void onSkipUpdateVersion() {
        decline(UpdateChoice.SKIP_VERSION);
    }

    private void decline(UpdateChoice choice) {
        UpdateManager.ReadyUpdate update = readyUpdate;
        if (update == null) return;
        UpdateManager.get().choose(choice, update);
        if (choice.remembersVersion()) {
            host.session().setSkippedUpdateVersion(update.version().toString());
            host.saveSession();
        }
        updateBar.setVisible(false);
        updateBar.setManaged(false);
    }
}
