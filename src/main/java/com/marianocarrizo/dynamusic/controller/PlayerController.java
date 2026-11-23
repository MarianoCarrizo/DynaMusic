package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.ambiance.YoutubePicker;
import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.config.SessionConfigManager;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.Playlist;
import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.model.Scene;
import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import com.marianocarrizo.dynamusic.service.TrackLibrary;
import com.marianocarrizo.dynamusic.service.UpdateManager;
import com.marianocarrizo.dynamusic.service.YouTubeDownloader;
import com.marianocarrizo.dynamusic.service.YtDlpManager;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PlayerController implements SceneHost, PlayerHost {

    private static final String CAFECITO_URL = "https://cafecito.app/duhonha";
    private HostServices hostServices;

    // Layout
    @FXML private VBox mainRoot;
    @FXML private Button btnMaximize;

    @FXML private UpdateBarController updateBarController;
    @FXML private SceneBarController sceneBarController;
    @FXML private AmbianceToolbarController ambianceToolbarController;
    @FXML private VolumeController volumeController;

    @FXML private MoodPanelController moodPanelController;
    @FXML private LiveAuraController liveAuraController;

    @FXML private VBox previewPanel;
    @FXML private ToggleButton btnPreviewLoop;
    @FXML private Button btnPreviewPlayPause;
    @FXML private SeekBar previewSeekBar;
    @FXML private Label lblPreviewTrack;

    @FXML private VBox onDemandPanel;
    @FXML private Label lblOnDemandTrack;
    @FXML private ToggleButton btnOnDemandLoop;
    @FXML private Button btnOnDemandPlayPause;
    @FXML private SeekBar onDemandSeekBar;

    // T-10: TabPane de modos
    @FXML private TabPane modeTabPane;
    @FXML private Tab moodsTab;
    @FXML private Tab playlistsTab;

    // T-11: Panel de gestión de playlists
    @FXML private VBox playlistPanel;
    @FXML private ComboBox<String> cmbPlaylist;
    @FXML private ListView<String> lstPlaylistTracks;
    @FXML private Label lblTrackCounter;

    // T-11: Transporte del playlist
    @FXML private ToggleButton btnShuffle;
    @FXML private Button btnPrev;
    @FXML private Button btnPlaylistPlayPause;
    @FXML private Button btnNext;
    @FXML private ToggleButton btnPlaylistLoop;

    // T-11: Progreso del playlist
    @FXML private SeekBar playlistSeekBar;

    // Lista y búsqueda
    @FXML private TextField txtSearch;
    @FXML private TreeView<File> treeFiles;

    @FXML private TextField txtYoutubeUrl;
    @FXML private Button btnAddYoutube;
    @FXML private Button btnPlayYoutube;
    @FXML private Button btnBrowseYoutube;
    @FXML private Label lblYoutubeError;

    private File rootFolder;

    private final MusicService musicService = new MusicService();
    private final SessionConfigManager manager = new SessionConfigManager();
    private final SessionConfig sessionConfig = new SessionConfig();
    private final YouTubeDownloader youTubeDownloader = new YouTubeDownloader();
    private final AmbianceCoordinator ambiance = new AmbianceCoordinator(
            sessionConfig, () -> manager.save(sessionConfig), this::liveSlot, this::refreshSceneEditorBackground);
    private SceneEditorController sceneEditor;

    private boolean applyingSession = false;
    private boolean refreshingUI = false;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "track-io");
        t.setDaemon(true);
        return t;
    });

    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(400));

    private final Map<TrackType, String> loadedPaths = new EnumMap<>(TrackType.class);

    private static final String DEFAULT_PLAYLIST_NAME = "New playlist";

    private PlaylistManager playlistManager;

    @FXML
    public void initialize() {
        startYtDlpManager();
        updateBarController.attach(this);
        sceneBarController.attach(this);
        ambianceToolbarController.attach(this);
        volumeController.attach(this);
        liveAuraController.attach(this);
        moodPanelController.attach(this);
        liveAuraController.refresh();

        saveDebounce.setOnFinished(e -> manager.save(sessionConfig));

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> rebuildTree());
        }

        if (treeFiles != null) {
            treeFiles.setCellFactory(tv -> new LibraryCell());
            treeFiles.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    TreeItem<File> selected = treeFiles.getSelectionModel().getSelectedItem();
                    if (selected != null && selected.getValue() != null && selected.getValue().isFile()) {
                        playNow(selected.getValue(), null);
                        e.consume();
                    }
                }
            });
            treeFiles.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    TreeItem<File> selected = treeFiles.getSelectionModel().getSelectedItem();
                    if (selected != null && selected.getValue() != null
                            && !selected.getValue().isDirectory()) {
                        startPreview(selected.getValue());
                    }
                }
            });
        }

        // Listener del reproductor de preescucha
        musicService.setPreviewProgressListener((current, total) ->
            Platform.runLater(() -> {
                previewSeekBar.showPlayback(current, total);
                updatePreviewPlayPauseButton();
            }));

        musicService.setOnDemandProgressListener((current, total) ->
            Platform.runLater(() -> {
                onDemandSeekBar.showPlayback(current, total);
                updateOnDemandPlayPauseButton();
            }));
        musicService.setOnDemandStateListener(() -> {
            if (!musicService.isOnDemandPlaying()) {
                onDemandSeekBar.rewindToStart();
            }
            updateOnDemandPlayPauseButton();
        });
        showOnDemandPanel(false);

        previewSeekBar.setOnSeekToFraction(musicService::seekPreview);

        musicService.setOnPreviewPausedByMode(() -> setPreviewButtonIcon("▶"));
        musicService.setOnModePausedByPreview(() -> {
            moodPanelController.showPlaying(false);
            if (btnOnDemandPlayPause != null) btnOnDemandPlayPause.setText("▶");
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("▶");
        });
        onDemandSeekBar.setOnSeekToFraction(musicService::seekOnDemand);
        playlistSeekBar.setOnSeekToFraction(musicService::seekPlaylist);

        updatePreviewPanel(false);
        ambiance.setAudioSource(musicService::getAudioFeatures);

        // T-12: PlaylistManager + wiring de callbacks
        playlistManager = new PlaylistManager();

        musicService.setOnPlaylistEnd(() -> Platform.runLater(() -> {
            playlistManager.next().ifPresentOrElse(
                f -> musicService.playPlaylistTrack(f),
                () -> { musicService.stopPlaylist(); refreshPlaylistUI(); }
            );
            refreshPlaylistUI();
        }));

        musicService.setPlaylistProgressListener((current, total) ->
            Platform.runLater(() -> playlistSeekBar.showPlayback(current, total)));

        if (cmbPlaylist != null) {
            cmbPlaylist.setOnAction(e -> {
                String name = cmbPlaylist.getValue();
                if (name == null || refreshingUI) return;
                playlistManager.setActivePlaylist(name);
                sessionConfig.setActivePlaylistName(name);
                manager.save(sessionConfig);
                refreshPlaylistUI();
            });
        }

        if (lstPlaylistTracks != null) {
            lstPlaylistTracks.setCellFactory(lv -> new PlaylistTrackCell(playlistTrackHost));
            PlaylistTrackCell.acceptFileDropsOnList(lstPlaylistTracks, playlistTrackHost);
            lstPlaylistTracks.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.DELETE) onRemoveTrack();
            });
            lstPlaylistTracks.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    int idx = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
                    if (idx >= 0) {
                        playlistManager.startAt(idx).ifPresent(f -> {
                            enterMode(MusicService.PlaybackMode.PLAYLIST);
                            musicService.playPlaylistTrack(f);
                            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("⏸");
                            refreshPlaylistUI();
                        });
                    }
                }
            });
        }

        if (modeTabPane != null) {
            modeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (applyingSession || newTab == null) return;
                boolean isPlaylist = newTab == playlistsTab;
                sessionConfig.setActiveMode(isPlaylist ? "playlist" : "mood");
                manager.save(sessionConfig);
                if (isPlaylist) refreshPlaylistUI();
            });
        }

        Platform.runLater(() -> {
            updateWindowMood(TrackType.NORMAL);
            ambiance.open();
        });
    }

    @Override
    public void slotBackgroundEdited() {
        manager.save(sessionConfig);
        ambiance.applyBackgroundIfChanged(liveSlot());
        refreshSceneEditorBackground();
    }

    private void refreshCards() {
        moodPanelController.refreshCards();
        liveAuraController.refresh();
    }

    @Override
    public void liveAuraEdited(AuraSlot slot) {
        onAuraEdited(slot);
        if (sceneEditor != null) sceneEditor.refreshAuras();
    }

    public void toggleLastUsedPlayer() {
        if (musicService.previewSoundedLast()) {
            onTogglePreviewPlayPause();
            return;
        }
        switch (musicService.getPlaybackMode()) {
            case MOOD -> moodPanelController.togglePlayPause();
            case PLAYLIST -> onPlaylistPlayPause();
            case ON_DEMAND -> onToggleOnDemandPlayPause();
        }
    }

    @Override
    public void switchTrack(TrackType type) {
        if (type == null || !musicService.isLoaded(type)) return;
        boolean fromOtherSource = musicService.getPlaybackMode() != MusicService.PlaybackMode.MOOD;
        if (!fromOtherSource && type == moodPanelController.activeMood() && musicService.getCurrentTrack() == type) return;
        enterMode(MusicService.PlaybackMode.MOOD);
        musicService.switchTrack(type);
        moodPanelController.setActiveMood(type);
        updateWindowMood(type);
        ambiance.activateSlot(AuraSlot.of(type));
        liveAuraController.refresh();
        moodPanelController.refreshButtonStates();
        moodPanelController.showPlaying(true);
        updatePreviewPanel(false);
        resetPreviewSlider();
    }

    private boolean usesOnDemandAura() {
        MusicService.PlaybackMode mode = musicService.getPlaybackMode();
        return mode == MusicService.PlaybackMode.ON_DEMAND || mode == MusicService.PlaybackMode.PLAYLIST;
    }

    @Override
    public AuraSlot liveSlot() {
        return usesOnDemandAura() ? AuraSlot.ON_DEMAND : AuraSlot.of(moodPanelController.activeMood());
    }

    @Override
    public void enterMode(MusicService.PlaybackMode target) {
        musicService.setPlaybackMode(target);
        syncSourceUi();
    }

    private void syncSourceUi() {
        MusicService.PlaybackMode mode = musicService.getPlaybackMode();
        if (mode != MusicService.PlaybackMode.ON_DEMAND) showOnDemandPanel(false);
        if (mode != MusicService.PlaybackMode.PLAYLIST) {
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("▶");
            if (lstPlaylistTracks != null) lstPlaylistTracks.refresh();
        }
        ambiance.activateSlot(liveSlot());
        liveAuraController.refresh();
        moodPanelController.syncPlayPauseIcon();
    }

    private void playNow(File file, String title) {
        if (file == null || !file.isFile()) return;
        String label = title != null && !title.isBlank() ? title : DisplayNames.stripExtension(file.getName());
        if (lblOnDemandTrack != null) lblOnDemandTrack.setText(label);
        onDemandSeekBar.showNoTrack();
        musicService.playOnDemand(file, label, () -> {
            showOnDemandPanel(true);
            ambiance.activateSlot(AuraSlot.ON_DEMAND);
            liveAuraController.refresh();
            moodPanelController.showPlaying(false);
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("▶");
            if (lstPlaylistTracks != null) lstPlaylistTracks.refresh();
            updateOnDemandPlayPauseButton();
        });
    }

    private void showOnDemandPanel(boolean show) {
        if (onDemandPanel == null) return;
        onDemandPanel.setVisible(show);
        onDemandPanel.setManaged(show);
    }

    private void updateOnDemandPlayPauseButton() {
        if (btnOnDemandPlayPause != null)
            btnOnDemandPlayPause.setText(musicService.isOnDemandPlaying() ? "⏸" : "▶");
    }

    @FXML
    private void onToggleOnDemandPlayPause() {
        if (musicService.isOnDemandPlaying()) musicService.onDemandPause();
        else musicService.onDemandPlay();
        updateOnDemandPlayPauseButton();
    }

    @FXML
    private void onOnDemandStop() {
        musicService.onDemandPause();
        musicService.seekOnDemand(0);
        onDemandSeekBar.rewindToStart();
        updateOnDemandPlayPauseButton();
    }

    @FXML
    private void onOnDemandClose() {
        enterMode(MusicService.PlaybackMode.MOOD);
        moodPanelController.showPlaying(false);
    }

    @FXML
    private void onToggleOnDemandLoop() {
        if (btnOnDemandLoop != null) musicService.setOnDemandLoop(btnOnDemandLoop.isSelected());
    }

    // --- Controles de preescucha ---

    private void startPreview(File file) {
        if (musicService.isPlaying()) musicService.fadeOutAndPause(null);
        if (musicService.isPlaylistPlaying()) musicService.playlistPause();
        if (lblPreviewTrack != null)
            lblPreviewTrack.setText(file.getName());
        musicService.previewTrack(file);
        updatePreviewPanel(true);
        updatePreviewPlayPauseButton();
        updateOnDemandPlayPauseButton();
    }

    @FXML
    private void onTogglePreviewPlayPause() {
        if (musicService.isPreviewPlaying()) {
            musicService.previewPause();
            setPreviewButtonIcon("▶");
        } else {
            musicService.previewPlay();
            setPreviewButtonIcon("⏸");
        }
    }

    private void setPreviewButtonIcon(String icon) {
        if (btnPreviewPlayPause != null) btnPreviewPlayPause.setText(icon);
    }

    @FXML
    private void onPreviewStop() {
        musicService.stopPreview();
        updatePreviewPanel(false);
        resetPreviewSlider();
    }

    // Llamado desde el listener de progreso del preview
    private void updatePreviewPlayPauseButton() {
        if (btnPreviewPlayPause != null)
            btnPreviewPlayPause.setText(musicService.isPreviewPlaying() ? "⏸" : "▶");
    }

    private void updatePreviewPanel(boolean active) {
        if (previewPanel != null) {
            previewPanel.setVisible(active);
            previewPanel.setManaged(active);
        }
        if (btnPreviewPlayPause != null) btnPreviewPlayPause.setDisable(!active);
        previewSeekBar.setSeekEnabled(active);
        if (!active && lblPreviewTrack != null)
            lblPreviewTrack.setText("No active preview");
    }

    private void resetPreviewSlider() {
        previewSeekBar.showNoTrack();
        updatePreviewPlayPauseButton();
    }

    private void updateWindowMood(TrackType type) {
        if (type == null || mainRoot == null) return;
        mainRoot.setId(switch (type) {
            case PAUSED    -> "window-paused";
            case RAGED     -> "window-raged";
            case NORMAL    -> "window-normal";
            case ADVANTAGE -> "window-advantage";
        });
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @FXML
    private void onOpenCafecito() {
        if (hostServices != null) hostServices.showDocument(CAFECITO_URL);
    }

    @Override
    public void applyActiveScene() {
        applyScene(sessionConfig.getActiveScene());
        if (sceneEditor != null) sceneEditor.refresh();
    }

    @Override
    public void openSceneEditor() {
        if (sceneEditor == null) {
            sceneEditor = SceneEditorController.create(mainRoot.getScene().getWindow(), this);
        }
        sceneEditor.show();
    }

    private void applyScene(Scene scene) {
        Map<TrackType, String> assignments = scene.getAssignments();
        for (TrackType t : TrackType.values()) {
            String path = assignments.get(t);
            if (path != null && new File(path).isFile()) {
                if (!path.equals(loadedPaths.get(t))) {
                    loadedPaths.put(t, path);
                    musicService.loadTrack(t, new File(path), () -> onTrackLoaded(t), () -> onTrackLoadFailed(t, path));
                }
            } else {
                loadedPaths.remove(t);
                musicService.unloadTrack(t);
            }
        }

        ambiance.applyScene(scene);

        refreshCards();
        moodPanelController.refreshButtonStates();
        moodPanelController.syncPlayPauseIcon();
        moodPanelController.resetProgressIfEmpty();
    }

    private void onTrackLoadFailed(TrackType type, String path) {
        if (!path.equals(loadedPaths.get(type))) return;
        loadedPaths.remove(type);
        musicService.unloadTrack(type);
        refreshCards();
        moodPanelController.refreshButtonStates();
        moodPanelController.syncPlayPauseIcon();
    }

    private void onTrackLoaded(TrackType type) {
        if (type == moodPanelController.activeMood() && !musicService.isPlaying()
                && musicService.getCurrentTrack() != type) {
            musicService.selectTrack(type);
        }
        refreshCards();
        moodPanelController.refreshButtonStates();
        moodPanelController.syncPlayPauseIcon();
    }

    @Override public SessionConfig session() { return sessionConfig; }

    @Override public MusicService musicService() { return musicService; }

    @Override public AmbianceCoordinator ambiance() { return ambiance; }

    @Override public Window window() { return mainRoot.getScene().getWindow(); }

    @Override public void saveSession() { manager.save(sessionConfig); }

    @Override public boolean isFfmpegAvailable() { return ambiance.isFfmpegAvailable(); }

    @Override
    public void onSceneRenamed() {
        sceneBarController.refresh();
    }

    @Override
    public boolean deleteActiveScene() {
        if (!sessionConfig.deleteScene(sessionConfig.getActiveScene().getId())) return false;
        manager.save(sessionConfig);
        sceneBarController.refresh();
        applyActiveScene();
        return true;
    }

    @Override
    public void onAuraEdited(AuraSlot slot) {
        if (slot == null) return;
        ambiance.setAura(slot, sessionConfig.getActiveScene().getAura(slot));
        refreshCards();
        saveDebounce.playFromStart();
    }

    @Override
    public void assignTrack(TrackType type, File file) {
        if (type == null || file == null || !file.isFile() || !isAudioFile(file)) return;
        final Scene target = sessionConfig.getActiveScene();
        final String source = file.getAbsolutePath();
        CompletableFuture.supplyAsync(() -> TrackLibrary.pin(source), ioExecutor)
            .whenComplete((pinned, err) -> Platform.runLater(() -> {
                String path = (err == null && pinned != null) ? pinned : source;
                target.setAssignment(type, path);
                manager.save(sessionConfig);
                if (target == sessionConfig.getActiveScene()) {
                    loadedPaths.put(type, path);
                    musicService.loadTrack(type, new File(path), () -> onTrackLoaded(type), () -> onTrackLoadFailed(type, path));
                    refreshCards();
                    if (sceneEditor != null) sceneEditor.refreshTracks();
                }
            }));
    }

    @Override
    public void clearTrack(TrackType type) {
        if (type == null) return;
        sessionConfig.getActiveScene().setAssignment(type, null);
        manager.save(sessionConfig);
        loadedPaths.remove(type);
        musicService.unloadTrack(type);
        moodPanelController.refreshButtonStates();
        moodPanelController.syncPlayPauseIcon();
        moodPanelController.resetProgressIfEmpty();
        refreshCards();
        if (sceneEditor != null) sceneEditor.refreshTracks();
    }

    @Override
    public void applyBackground() {
        ambiance.applyBackgroundIfChanged(liveSlot());
    }

    @Override
    public void applyFitMode() {
        ambiance.applyFitMode();
    }

    private void refreshSceneEditorBackground() {
        if (sceneEditor != null) sceneEditor.refreshBackground();
    }

    public void setFfmpegAvailable(boolean available) {
        ambiance.setFfmpegAvailable(available);
    }

    private final PlaylistTrackCell.Host playlistTrackHost = new PlaylistTrackCell.Host() {
        @Override public boolean isNowPlaying(int index) {
            return musicService.isPlaylistPlaying() && index == playlistManager.currentDisplayIndex();
        }
        @Override public void filesDropped(List<File> files, int slot) {
            addDroppedFilesToPlaylist(files, slot);
        }
        @Override public void trackMoved(int from, int slot) {
            moveTrackToSlot(from, slot);
        }
        @Override public void removeRequested(int index) {
            removeTrackAt(index);
        }
    };

    private final class LibraryCell extends TreeCell<File> {
        private final ContextMenu fileMenu = new ContextMenu();
        private final ContextMenu folderMenu = new ContextMenu();
        private final Menu filePlaylistMenu = new Menu("Add to playlist");
        private final Menu folderPlaylistMenu = new Menu("Add to playlist");

        LibraryCell() {
            Menu assign = new Menu("Assign to");
            assign.getItems().addAll(
                    assignItem("Paused", TrackType.PAUSED),
                    assignItem("Normal", TrackType.NORMAL),
                    assignItem("Raged", TrackType.RAGED),
                    assignItem("Advantage", TrackType.ADVANTAGE));
            MenuItem playNow = new MenuItem("Play now");
            playNow.setOnAction(e -> {
                File f = getItem();
                if (f != null && f.isFile()) playNow(f, null);
            });
            fileMenu.getItems().addAll(playNow, assign, filePlaylistMenu);
            fileMenu.setOnShowing(e -> fillPlaylistMenu(filePlaylistMenu));

            MenuItem newFromFolder = new MenuItem("New playlist from folder");
            newFromFolder.setOnAction(e -> createPlaylistFromFolder(getItem()));
            folderMenu.getItems().addAll(folderPlaylistMenu, newFromFolder);
            folderMenu.setOnShowing(e -> fillPlaylistMenu(folderPlaylistMenu));

            setOnDragDetected(e -> {
                File f = getItem();
                if (f == null) return;
                Dragboard db = startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putFiles(List.of(f));
                db.setContent(content);
                e.consume();
            });
        }

        private void fillPlaylistMenu(Menu target) {
            target.getItems().clear();
            for (Playlist playlist : playlistManager.getPlaylists()) {
                MenuItem entry = new MenuItem(playlist.getName());
                entry.setOnAction(e -> addLibraryItemToPlaylist(playlist.getName(), getItem()));
                target.getItems().add(entry);
            }
            if (!target.getItems().isEmpty()) target.getItems().add(new SeparatorMenuItem());
            MenuItem created = new MenuItem("New playlist...");
            created.setOnAction(e -> promptNewPlaylistName().ifPresent(
                    name -> addLibraryItemToPlaylist(name, getItem())));
            target.getItems().add(created);
        }

        private MenuItem assignItem(String label, TrackType type) {
            MenuItem item = new MenuItem(label);
            item.setOnAction(e -> {
                File f = getItem();
                if (f != null && f.isFile()) assignTrack(type, f);
            });
            return item;
        }

        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            setText(item.isDirectory() ? "📁 " + item.getName() : item.getName());
            setContextMenu(item.isDirectory() ? folderMenu : fileMenu);
        }
    }

    // --- T-13: CRUD de playlists ---

    @FXML private void onNewPlaylist() {
        promptNewPlaylistName();
    }

    private Optional<String> promptNewPlaylistName() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("New Playlist");
        d.setHeaderText(null);
        d.setContentText("Name:");
        Optional<String> created = d.showAndWait().map(String::trim).filter(n -> !n.isEmpty()).flatMap(name -> {
            if (playlistManager.createPlaylist(name) == null) {
                new Alert(Alert.AlertType.WARNING, "Name already exists or is blank.").showAndWait();
                return Optional.empty();
            }
            return Optional.of(name);
        });
        if (created.isPresent()) {
            activatePlaylist(created.get());
            refreshPlaylistUI();
            persistPlaylists();
        }
        return created;
    }

    private void addLibraryItemToPlaylist(String playlistName, File source) {
        if (source == null) return;
        boolean isFolder = source.isDirectory();
        collectInBackground(() -> AudioFileCollector.collect(source), tracks -> {
            if (tracks.isEmpty()) {
                if (isFolder) showNoAudioFilesMessage();
                return;
            }
            addTracksToPlaylist(playlistName, tracks, Integer.MAX_VALUE, false);
        });
    }

    private void createPlaylistFromFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        collectInBackground(() -> AudioFileCollector.collect(folder), tracks -> {
            if (tracks.isEmpty()) {
                showNoAudioFilesMessage();
                return;
            }
            String name = playlistManager.ensurePlaylist(playlistNameFor(folder)).getName();
            addTracksToPlaylist(name, tracks, Integer.MAX_VALUE, true);
        });
    }

    private void addDroppedFilesToPlaylist(List<File> dropped, int slot) {
        collectInBackground(() -> AudioFileCollector.collectAll(dropped), tracks -> {
            if (tracks.isEmpty()) return;
            String target = playlistManager.getActivePlaylistName();
            if (target == null) target = playlistManager.ensurePlaylist(DEFAULT_PLAYLIST_NAME).getName();
            addTracksToPlaylist(target, tracks, slot, false);
        });
    }

    private void collectInBackground(Supplier<List<File>> collector, Consumer<List<File>> onCollected) {
        CompletableFuture.supplyAsync(collector, ioExecutor).whenComplete((tracks, error) ->
                Platform.runLater(() -> onCollected.accept(error == null ? tracks : List.of())));
    }

    private void showNoAudioFilesMessage() {
        new Alert(Alert.AlertType.INFORMATION, "No audio files (mp3, wav, flac) found in that folder.").showAndWait();
    }

    private static String playlistNameFor(File folder) {
        String name = folder.getName();
        if (name.isEmpty()) name = folder.getPath().replaceAll("[\\\\/]+$", "");
        return name.isEmpty() ? DEFAULT_PLAYLIST_NAME : name;
    }

    private void addTracksToPlaylist(String playlistName, List<File> tracks, int slot, boolean activate) {
        String activeBefore = playlistManager.getActivePlaylistName();
        boolean isActive = playlistName.equalsIgnoreCase(activeBefore);
        int selected = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
        int added = playlistManager.insertTracks(playlistName, tracks, slot);
        if (activate || activeBefore == null) {
            activatePlaylist(playlistName);
            if (!isActive) selected = -1;
        }
        if (isActive && slot <= selected) selected += added;
        refreshPlaylistUI();
        selectTrackRow(selected);
        persistPlaylists();
    }

    private void selectTrackRow(int index) {
        if (lstPlaylistTracks == null || index < 0 || index >= lstPlaylistTracks.getItems().size()) return;
        lstPlaylistTracks.getSelectionModel().select(index);
    }

    private void activatePlaylist(String name) {
        playlistManager.setActivePlaylist(name);
        sessionConfig.setActivePlaylistName(name);
    }

    private void moveTrackToSlot(int from, int slot) {
        String active = playlistManager.getActivePlaylistName();
        if (active == null) return;
        int to = slot > from ? slot - 1 : slot;
        if (to == from) return;
        playlistManager.reorderTrack(active, from, to);
        refreshPlaylistUI();
        lstPlaylistTracks.getSelectionModel().select(to);
        persistPlaylists();
    }

    private void removeTrackAt(int index) {
        String active = playlistManager.getActivePlaylistName();
        if (active == null || index < 0) return;
        playlistManager.removeTrack(active, index);
        refreshPlaylistUI();
        selectTrackRow(Math.min(index, lstPlaylistTracks.getItems().size() - 1));
        persistPlaylists();
    }

    @FXML private void onRenamePlaylist() {
        String current = cmbPlaylist.getValue();
        if (current == null) return;
        TextInputDialog d = new TextInputDialog(current);
        d.setTitle("Rename");
        d.setHeaderText(null);
        d.setContentText("New name:");
        d.showAndWait().map(String::trim).filter(n -> !n.isEmpty()).ifPresent(name -> {
            if (!playlistManager.renamePlaylist(current, name)) {
                new Alert(Alert.AlertType.WARNING, "Name already exists or is blank.").showAndWait();
                return;
            }
            refreshPlaylistUI();
            persistPlaylists();
        });
    }

    @FXML private void onDeletePlaylist() {
        String name = cmbPlaylist.getValue();
        if (name == null) return;
        boolean isEmpty = playlistManager.getPlaylists().stream()
            .filter(p -> p.getName().equals(name))
            .findFirst()
            .map(p -> p.getPaths().isEmpty())
            .orElse(true);
        if (!isEmpty) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete playlist \"" + name + "\" and all its tracks?");
            if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        }
        playlistManager.deletePlaylist(name);
        refreshPlaylistUI();
        persistPlaylists();
    }

    @FXML private void onAddToPlaylist() {
        String pname = cmbPlaylist.getValue();
        if (pname == null || treeFiles == null) return;
        TreeItem<File> sel = treeFiles.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getValue() == null) return;
        addLibraryItemToPlaylist(pname, sel.getValue());
    }

    @FXML private void onRemoveTrack() {
        removeTrackAt(lstPlaylistTracks.getSelectionModel().getSelectedIndex());
    }

    @FXML private void onMoveUp() {
        String pname = cmbPlaylist.getValue();
        if (pname == null) return;
        int idx = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return;
        playlistManager.reorderTrack(pname, idx, idx - 1);
        refreshPlaylistUI();
        lstPlaylistTracks.getSelectionModel().select(idx - 1);
        persistPlaylists();
    }

    @FXML private void onMoveDown() {
        String pname = cmbPlaylist.getValue();
        if (pname == null) return;
        int idx = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= lstPlaylistTracks.getItems().size() - 1) return;
        playlistManager.reorderTrack(pname, idx, idx + 1);
        refreshPlaylistUI();
        lstPlaylistTracks.getSelectionModel().select(idx + 1);
        persistPlaylists();
    }

    @FXML
    private void onAddYoutubeUrl() {
        if (lblYoutubeError != null) { lblYoutubeError.setText(""); lblYoutubeError.setVisible(false); lblYoutubeError.setManaged(false); }

        String pname = cmbPlaylist.getValue();
        if (pname == null || pname.isBlank()) {
            showYoutubeError("Select a playlist first.");
            return;
        }

        String url = txtYoutubeUrl != null ? txtYoutubeUrl.getText().trim() : "";
        Optional<String> vid = YouTubeDownloader.extractVideoId(url);
        if (vid.isEmpty()) {
            showYoutubeError("Not a valid YouTube URL.");
            return;
        }

        final String capturedUrl = url;
        final String capturedVid = vid.get();
        final String placeholderText = "⬇ Downloading... (" + capturedVid + ")";

        if (btnAddYoutube != null) btnAddYoutube.setDisable(true);
        lstPlaylistTracks.getItems().add(placeholderText);
        if (txtYoutubeUrl != null) txtYoutubeUrl.clear();

        youTubeDownloader.downloadAsync(capturedUrl, capturedVid,
                result -> Platform.runLater(() -> {
                    PlaylistItem item = new PlaylistItem(
                            result.getPath(), capturedUrl, result.getTitle());
                    playlistManager.addItem(pname, item);
                    persistPlaylists();
                    if (btnAddYoutube != null) btnAddYoutube.setDisable(false);
                    refreshPlaylistUI();
                }),
                err -> Platform.runLater(() -> {
                    if (btnAddYoutube != null) btnAddYoutube.setDisable(false);
                    refreshPlaylistUI();
                    new Alert(Alert.AlertType.ERROR, "Download failed: " + err).showAndWait();
                })
        );
    }

    @FXML
    private void onPlayYoutubeUrl() {
        if (lblYoutubeError != null) { lblYoutubeError.setText(""); lblYoutubeError.setVisible(false); lblYoutubeError.setManaged(false); }
        String url = txtYoutubeUrl != null ? txtYoutubeUrl.getText().trim() : "";
        playYoutubeNow(url);
    }

    private void playYoutubeNow(String url) {
        Optional<String> vid = YouTubeDownloader.extractVideoId(url);
        if (vid.isEmpty()) {
            showYoutubeError("Not a valid YouTube URL.");
            return;
        }
        if (btnPlayYoutube != null) btnPlayYoutube.setDisable(true);
        showYtDlpStatus("Downloading track...");
        youTubeDownloader.downloadAsync(url, vid.get(),
                result -> Platform.runLater(() -> {
                    if (btnPlayYoutube != null) btnPlayYoutube.setDisable(false);
                    ytDlpStatusClear.playFromStart();
                    if (txtYoutubeUrl != null) txtYoutubeUrl.clear();
                    playNow(new File(result.getPath()), result.displayName());
                }),
                err -> Platform.runLater(() -> {
                    if (btnPlayYoutube != null) btnPlayYoutube.setDisable(false);
                    showYoutubeError("Download failed: " + err);
                })
        );
    }

    @FXML
    private void onBrowseYouTube() {
        Stage owner = (Stage) btnBrowseYoutube.getScene().getWindow();
        YoutubePicker.show(owner, youTubeDownloader, result -> {
            playYoutubeNow(result.url());
        });
    }

    private final PauseTransition ytDlpStatusClear = new PauseTransition(javafx.util.Duration.seconds(5));

    private void startYtDlpManager() {
        ytDlpStatusClear.setOnFinished(e -> {
            if (lblYoutubeError != null) {
                lblYoutubeError.setText("");
                lblYoutubeError.setVisible(false);
                lblYoutubeError.setManaged(false);
            }
        });
        YtDlpManager mgr = YtDlpManager.get();
        mgr.setStatusListener(msg -> Platform.runLater(() -> showYtDlpStatus(msg)));
        mgr.ensureReadyAsync();
    }

    private void showYtDlpStatus(String message) {
        if (lblYoutubeError == null) return;
        boolean error = message.startsWith("Could not");
        lblYoutubeError.setText(message);
        lblYoutubeError.setStyle(error ? "" : "-fx-text-fill: #8a8f98;");
        lblYoutubeError.setVisible(true);
        lblYoutubeError.setManaged(true);
        ytDlpStatusClear.stop();
        if (!message.endsWith("...")) {
            ytDlpStatusClear.setDuration(javafx.util.Duration.seconds(error ? 10 : 4));
            ytDlpStatusClear.playFromStart();
        }
    }

    private void showYoutubeError(String message) {
        if (lblYoutubeError != null) {
            ytDlpStatusClear.stop();
            lblYoutubeError.setStyle("");
            lblYoutubeError.setText(message);
            lblYoutubeError.setVisible(true);
            lblYoutubeError.setManaged(true);
        } else {
            new Alert(Alert.AlertType.ERROR, message).showAndWait();
        }
    }

    private void persistPlaylists() {
        sessionConfig.setPlaylists(playlistManager.getPlaylists());
        manager.save(sessionConfig);
    }

    // --- T-14: Transporte y refresh del playlist ---

    @FXML private void onPlaylistPlayPause() {
        if (musicService.isPlaylistPlaying()) {
            musicService.playlistPause();
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("▶");
        } else {
            playlistManager.startCurrent().ifPresent(f -> {
                enterMode(MusicService.PlaybackMode.PLAYLIST);
                musicService.playPlaylistTrack(f);
                if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("⏸");
            });
        }
    }

    @FXML private void onPrev() {
        playlistManager.prev().ifPresent(f -> {
            enterMode(MusicService.PlaybackMode.PLAYLIST);
            musicService.playPlaylistTrack(f);
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("⏸");
        });
        refreshPlaylistUI();
    }

    @FXML private void onNext() {
        playlistManager.next().ifPresentOrElse(f -> {
            enterMode(MusicService.PlaybackMode.PLAYLIST);
            musicService.playPlaylistTrack(f);
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("⏸");
        }, () -> {
            musicService.stopPlaylist();
            if (btnPlaylistPlayPause != null) btnPlaylistPlayPause.setText("▶");
        });
        refreshPlaylistUI();
    }

    @FXML private void onToggleShuffle() {
        playlistManager.setShuffle(btnShuffle != null && btnShuffle.isSelected());
        refreshPlaylistUI();
    }

    @FXML private void onTogglePlaylistLoop() {
        playlistManager.setLoop(btnPlaylistLoop != null && btnPlaylistLoop.isSelected());
    }

    private void refreshPlaylistUI() {
        if (cmbPlaylist == null || refreshingUI) return;
        refreshingUI = true;
        try {
        // Repopulate playlist ComboBox
        String current = cmbPlaylist.getValue();
        cmbPlaylist.getItems().setAll(
            playlistManager.getPlaylists().stream()
                .map(p -> p.getName())
                .toList()
        );
        String active = playlistManager.getActivePlaylistName();
        if (active != null && cmbPlaylist.getItems().contains(active))
            cmbPlaylist.setValue(active);
        else if (current != null && cmbPlaylist.getItems().contains(current))
            cmbPlaylist.setValue(current);

        if (lstPlaylistTracks != null) {
            int selected = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
            lstPlaylistTracks.getItems().setAll(
                playlistManager.getActiveItems().stream()
                    .map(PlaylistItem::displayName)
                    .toList()
            );
            selectTrackRow(selected);
            lstPlaylistTracks.refresh();
        }

        // Update counter
        if (lblTrackCounter != null)
            lblTrackCounter.setText(playlistManager.positionLabel());
        } finally {
            refreshingUI = false;
        }
    }


    public void applySession(SessionConfig cfg) {
        if (cfg == null) return;
        sessionConfig.adoptScenesFrom(cfg);

        String lastImgDir = cfg.getLastImageFolderPath();
        if (lastImgDir != null) {
            sessionConfig.setLastImageFolderPath(lastImgDir);
        }

        // Restaurar índice de pantalla de ambiance
        int screenIdx = cfg.getAmbianceScreenIndex();
        sessionConfig.setAmbianceScreenIndex(screenIdx);
        Platform.runLater(() -> ambianceToolbarController.restoreScreen(screenIdx));

        String lastPath = cfg.getLastFolderPath();
        if (lastPath != null) {
            File last = new File(lastPath);
            if (last.isDirectory()) {
                rootFolder = last;
                sessionConfig.setLastFolderPath(lastPath);
                Platform.runLater(this::rebuildTree);
            }
        }

        // T-15: Restore playlists
        playlistManager.setPlaylists(cfg.getPlaylists());
        sessionConfig.setPlaylists(cfg.getPlaylists());
        sessionConfig.setActiveMode(cfg.getActiveMode());
        sessionConfig.setActivePlaylistName(cfg.getActivePlaylistName());
        refreshPlaylistUI();

        // T-15: Restore active mode
        String savedMode = cfg.getActiveMode();
        if ("playlist".equalsIgnoreCase(savedMode) && modeTabPane != null && playlistsTab != null) {
            applyingSession = true;
            modeTabPane.getSelectionModel().select(playlistsTab);
            String savedPl = cfg.getActivePlaylistName();
            if (savedPl != null) {
                playlistManager.setActivePlaylist(savedPl);
                if (cmbPlaylist != null) cmbPlaylist.setValue(savedPl);
            }
            refreshPlaylistUI();
            applyingSession = false;
        }

        // Aplicar en UI (puede ejecutarse antes de show(); Platform.runLater es seguro)
        Platform.runLater(() -> {
            // Restaurar volumen maestro desde sesión
            double mv = cfg.getMasterVolume();
            volumeController.restore(mv);
            sceneBarController.refresh();
            applyScene(sessionConfig.getActiveScene());
        });
    }

    // --- Carpeta y búsqueda ---

    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select music folder");
        String lastPath = sessionConfig.getLastFolderPath();
        if (lastPath != null) {
            File last = new File(lastPath);
            if (last.isDirectory()) chooser.setInitialDirectory(last);
        }
        File folder = chooser.showDialog(null);
        if (folder == null || !folder.isDirectory()) return;
        rootFolder = folder;
        sessionConfig.setLastFolderPath(folder.getAbsolutePath());
        manager.save(sessionConfig);
        if (txtSearch != null) txtSearch.clear();
        rebuildTree();
    }

    private void rebuildTree() {
        if (treeFiles == null || rootFolder == null) return;
        String filter = txtSearch != null ? txtSearch.getText().toLowerCase() : "";
        TreeItem<File> root = new TreeItem<>(rootFolder);
        root.setExpanded(true);
        populateTree(root, rootFolder, filter);
        treeFiles.setRoot(root);
    }

    private void populateTree(TreeItem<File> parent, File dir, String filter) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries, Comparator.comparing(File::isFile)  // carpetas primero
                              .thenComparing(f -> f.getName().toLowerCase()));
        for (File entry : entries) {
            if (entry.isDirectory()) {
                TreeItem<File> dirItem = new TreeItem<>(entry);
                populateTree(dirItem, entry, filter);
                if (!dirItem.getChildren().isEmpty()) {
                    dirItem.setExpanded(false);
                    parent.getChildren().add(dirItem);
                }
            } else if (isAudioFile(entry)) {
                if (filter.isEmpty() || entry.getName().toLowerCase().contains(filter)) {
                    parent.getChildren().add(new TreeItem<>(entry));
                }
            }
        }
    }

    private boolean isAudioFile(File f) {
        return AudioFileCollector.isAudioFile(f);
    }

    // --- Controles de ventana ---

    @FXML
    private void onTogglePreviewLoop() {
        if (btnPreviewLoop != null) musicService.setPreviewLoop(btnPreviewLoop.isSelected());
    }

    @FXML
    public void onMaximizeRestoreWindow(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
        btnMaximize.setText(stage.isMaximized() ? "❐" : "▢");
    }

    @FXML
    private void onCloseWindow(ActionEvent event) {
        shutdownApp();
    }

    @Override
    public void shutdownApp() {
        if (sceneEditor != null) sceneEditor.dispose();
        if (saveDebounce.getStatus() == Animation.Status.RUNNING) {
            saveDebounce.stop();
            manager.save(sessionConfig);
        }
        UpdateManager.get().launchPendingInstaller();
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void onMinimizeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

}
