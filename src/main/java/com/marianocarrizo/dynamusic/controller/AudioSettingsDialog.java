package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class AudioSettingsDialog {

    private static final String TITLE = "Audio settings";
    private static final String LIMIT_LABEL = "Maximum cache size";
    private static final String CLEAR_LABEL = "Clear cache";
    private static final double CONTENT_SPACING = 10;
    private static final double CONTENT_PADDING = 16;

    private AudioSettingsDialog() {}

    static void show(Window owner, CacheLimit current, LongSupplier usedBytes,
                     BiConsumer<CacheLimit, Runnable> onLimitChosen, Consumer<Runnable> onClear, Node streamingSection) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.setHeaderText(null);
        ComboBox<CacheLimit> choice = new ComboBox<>(FXCollections.observableArrayList(CacheLimit.values()));
        choice.setValue(current);
        Label usage = new Label();
        usage.getStyleClass().add("now-playing-detail");
        Runnable refresh = () -> usage.setText(CacheUsageText.usage(usedBytes.getAsLong(), choice.getValue()));
        choice.valueProperty().addListener((observable, previous, chosen) -> onLimitChosen.accept(chosen, refresh));
        Button clear = new Button(CLEAR_LABEL);
        clear.getStyleClass().add("btn-secondary");
        clear.setOnAction(event -> onClear.accept(refresh));
        refresh.run();
        VBox content = new VBox(CONTENT_SPACING, new Label(LIMIT_LABEL), choice, usage, clear, streamingSection);
        content.setPadding(new Insets(CONTENT_PADDING));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ThemedDialog.theme(dialog, owner);
        dialog.showAndWait();
    }
}
