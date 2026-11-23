package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.Window;

final class ThemedDialog {

    private static final String STYLESHEET = "/styles/dark-theme.css";

    private ThemedDialog() {}

    static boolean confirm(Window owner, String title, String message,
                           String confirmLabel, String cancelLabel) {
        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(cancelLabel, ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.NONE, message, confirm, cancel);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        style(alert, confirm, cancel);
        copyIcons(owner, alert);
        return alert.showAndWait().filter(b -> b == confirm).isPresent();
    }

    static void theme(Dialog<?> dialog, Window owner) {
        var dialogPane = dialog.getDialogPane();
        var css = ThemedDialog.class.getResource(STYLESHEET);
        if (css != null) dialogPane.getStylesheets().add(css.toExternalForm());
        dialogPane.getStyleClass().add("main-container");
        Button close = (Button) dialogPane.lookupButton(ButtonType.CLOSE);
        if (close != null) close.getStyleClass().add("btn-primary");
        if (owner != null) dialog.initOwner(owner);
        copyIcons(owner, dialog);
    }

    private static void style(Alert alert, ButtonType confirm, ButtonType cancel) {
        var dialogPane = alert.getDialogPane();
        var css = ThemedDialog.class.getResource(STYLESHEET);
        if (css != null) dialogPane.getStylesheets().add(css.toExternalForm());
        dialogPane.getStyleClass().add("main-container");
        Button confirmButton = (Button) dialogPane.lookupButton(confirm);
        Button cancelButton = (Button) dialogPane.lookupButton(cancel);
        confirmButton.getStyleClass().add("btn-secondary");
        cancelButton.getStyleClass().add("btn-primary");
        confirmButton.setDefaultButton(false);
        cancelButton.setDefaultButton(true);
        cancelButton.setCancelButton(true);
    }

    private static void copyIcons(Window owner, Dialog<?> alert) {
        if (owner instanceof Stage ownerStage
                && alert.getDialogPane().getScene().getWindow() instanceof Stage dialogStage) {
            dialogStage.getIcons().setAll(ownerStage.getIcons());
        }
    }
}
