package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class LibraryPanelController {

    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "library-scan");
        thread.setDaemon(true);
        return thread;
    });

    interface Host extends LibraryCell.Host {
        SessionConfig session();

        void saveSession();

        void startPreview(File file);

        void focusYoutubeUrl();
    }


    @FXML private TextField txtSearch;
    @FXML private TreeView<File> treeFiles;
    @FXML private VBox emptyState;
    @FXML private Label loadingLabel;

    private Host host;
    private File rootFolder;
    private File pendingReveal;
    private final AtomicInteger scanGeneration = new AtomicInteger();
    private final BooleanProperty loading = new SimpleBooleanProperty();

    void attach(Host host) {
        this.host = host;
        emptyState.visibleProperty().bind(treeFiles.rootProperty().isNull().and(loading.not()));
        loadingLabel.visibleProperty().bind(treeFiles.rootProperty().isNull().and(loading));
        treeFiles.visibleProperty().bind(treeFiles.rootProperty().isNotNull());
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> rebuildTree());
        treeFiles.setCellFactory(tv -> new LibraryCell(host));
        FfmpegLocator.onResolved(() -> Platform.runLater(treeFiles::refresh));
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
        treeFiles.setRoot(null);
        rebuildTree();
    }

    void reveal(File file) {
        Optional<File> root = LibraryRoot.rootFor(rootFolder, file);
        if (root.isEmpty()) return;
        if (rootFolder == null || !LibraryRoot.isSameFile(root.get(), rootFolder)) {
            showFolder(root.get());
            pendingReveal = file;
        } else if (!txtSearch.getText().isEmpty()) {
            pendingReveal = file;
            txtSearch.clear();
        } else if (loading.get()) {
            pendingReveal = file;
        } else {
            select(file);
        }
    }

    private void select(File file) {
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
        int generation = scanGeneration.incrementAndGet();
        LibraryScanner scanner = new LibraryScanner(
                txtSearch.getText().toLowerCase(), () -> scanGeneration.get() != generation);
        File folder = rootFolder;
        loading.set(true);
        Task<TreeItem<File>> scan = new Task<>() {
            @Override
            protected TreeItem<File> call() {
                return scanner.scan(folder);
            }
        };
        scan.setOnSucceeded(e -> applyScan(generation, scan.getValue()));
        scan.setOnFailed(e -> abandonScan(generation));
        SCAN_EXECUTOR.execute(scan);
    }

    private void applyScan(int generation, TreeItem<File> root) {
        if (generation != scanGeneration.get()) return;
        loading.set(false);
        treeFiles.setRoot(root);
        if (pendingReveal == null) return;
        File file = pendingReveal;
        pendingReveal = null;
        select(file);
    }

    private void abandonScan(int generation) {
        if (generation != scanGeneration.get()) return;
        loading.set(false);
        pendingReveal = null;
    }
}
