package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public final class LibraryPanelController {

    interface Host extends LibraryCell.Host {
        SessionConfig session();

        void saveSession();

        void startPreview(File file);
    }


    @FXML private TextField txtSearch;
    @FXML private TreeView<File> treeFiles;

    private Host host;
    private File rootFolder;

    void attach(Host host) {
        this.host = host;
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> rebuildTree());
        treeFiles.setCellFactory(tv -> new LibraryCell(host));
        treeFiles.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                selectedItem().filter(File::isFile).ifPresent(file -> {
                    host.playNow(file, null);
                    e.consume();
                });
            }
        });
        treeFiles.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                selectedItem().filter(file -> !file.isDirectory()).ifPresent(host::startPreview);
            }
        });
    }

    Optional<File> selectedItem() {
        TreeItem<File> selected = treeFiles.getSelectionModel().getSelectedItem();
        return selected == null ? Optional.empty() : Optional.ofNullable(selected.getValue());
    }

    void restoreFolder(String path) {
        if (path == null) return;
        File last = new File(path);
        if (!last.isDirectory()) return;
        rootFolder = last;
        host.session().setLastFolderPath(path);
        Platform.runLater(this::rebuildTree);
    }

    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select music folder");
        String lastPath = host.session().getLastFolderPath();
        if (lastPath != null) {
            File last = new File(lastPath);
            if (last.isDirectory()) chooser.setInitialDirectory(last);
        }
        File folder = chooser.showDialog(null);
        if (folder == null || !folder.isDirectory()) return;
        rootFolder = folder;
        host.session().setLastFolderPath(folder.getAbsolutePath());
        host.saveSession();
        txtSearch.clear();
        rebuildTree();
    }

    private void rebuildTree() {
        if (rootFolder == null) return;
        String filter = txtSearch.getText().toLowerCase();
        TreeItem<File> root = new TreeItem<>(rootFolder);
        root.setExpanded(true);
        populateTree(root, rootFolder, filter);
        treeFiles.setRoot(root);
    }

    private void populateTree(TreeItem<File> parent, File dir, String filter) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries, Comparator.comparing(File::isFile).thenComparing(f -> f.getName().toLowerCase()));
        for (File entry : entries) {
            if (entry.isDirectory()) {
                TreeItem<File> dirItem = new TreeItem<>(entry);
                populateTree(dirItem, entry, filter);
                if (!dirItem.getChildren().isEmpty()) {
                    dirItem.setExpanded(false);
                    parent.getChildren().add(dirItem);
                }
            } else if (AudioFileCollector.isAudioFile(entry)) {
                if (filter.isEmpty() || entry.getName().toLowerCase().contains(filter)) {
                    parent.getChildren().add(new TreeItem<>(entry));
                }
            }
        }
    }
}
