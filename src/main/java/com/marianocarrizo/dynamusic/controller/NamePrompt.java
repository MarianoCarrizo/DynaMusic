package com.marianocarrizo.dynamusic.controller;

import javafx.scene.control.TextInputDialog;

import java.util.Optional;

final class NamePrompt {

    private NamePrompt() {
    }

    static Optional<String> ask(String title, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty());
    }
}
