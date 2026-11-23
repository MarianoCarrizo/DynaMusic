package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
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

        void focusYoutubeUrl();
    }


    @FXML private TextField txtSearch;
    @FXML private TreeView<File> treeFiles;
    @FXML private VBox emptyState;

    private Host host;
    private File rootFolder;

    void attach(Host host) {
        this.host = host;
        emptyState.visibleProperty().bind(treeFiles.rootProperty().isNull());
        treeFiles.visibleProperty().bind(treeFiles.rootProperty().isNotNull());
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
        showFolder(chooser.showDialog(null));
    }

    @FXML
    private void onPasteYoutubeLink() {
        host.focusYoutubeUrl();
    }

    void showFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        rootFolder = folder;
        host.session().setLastFolderPath(folder.getAbsolutePath());
        host.saveSession();
        txtSearch.clear();
        rebuildTree();
    }

    void reveal(File file) {
        File root = LibraryRoot.rootFor(rootFolder, file);
        if (rootFolder == null || !LibraryRoot.isSameFile(root, rootFolder)) {
            showFolder(root);
        } else if (!txtSearch.getText().isEmpty()) {
            txtSearch.clear();
        }
        TreeItem<File> item = findItem(treeFiles.getRoot(), file);
        if (item == null) return;
        for (TreeItem<File> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
        treeFiles.getSelectionModel().select(item);
        treeFiles.scrollTo(treeFiles.getRow(item));
    }

    private static TreeItem<File> findItem(TreeItem<File> node, File target) {
        if (node == null) return null;
        if (node.getValue() != null && LibraryRoot.isSameFile(node.getValue(), target)) return node;
        for (TreeItem<File> child : node.getChildren()) {
            TreeItem<File> found = findItem(child, target);
            if (found != null) return found;
        }
        return null;
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
