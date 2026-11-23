package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.config.SessionConfigManager;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.Playlist;
import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.model.Scene;
import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import com.marianocarrizo.dynamusic.service.UpdateManager;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class PlayerController implements SceneHost, UpdateBarController.Host, SceneBarController.Host,
        AmbianceToolbarController.Host, VolumeController.Host, LiveAuraController.Host, MoodPanelController.Host,
        LibraryPanelController.Host, PreviewPanelController.Host, OnDemandPanelController.Host,
        PlaylistPanelController.Host, PlaylistTransportController.Host, YouTubeBarController.Host,
        NowPlayingBarController.Host {

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

    @FXML private PreviewPanelController previewPanelController;
    @FXML private OnDemandPanelController onDemandPanelController;
    @FXML private NowPlayingBarController nowPlayingBarController;

    // T-10: TabPane de modos
    @FXML private TabPane modeTabPane;
    @FXML private Tab moodsTab;
    @FXML private Tab playlistsTab;

    @FXML private PlaylistPanelController playlistPanelController;
    @FXML private PlaylistTransportController playlistTransportController;

    @FXML private LibraryPanelController libraryPanelController;
    @FXML private YouTubeBarController youTubeBarController;

    private final MusicService musicService = new MusicService();
    private final SessionConfigManager manager = new SessionConfigManager();
    private final SessionConfig sessionConfig = new SessionConfig();
    private final AmbianceCoordinator ambiance = new AmbianceCoordinator(
            sessionConfig, () -> manager.save(sessionConfig), this::liveSlot, this::refreshSceneEditorBackground);
    private SceneEditorController sceneEditor;

    private boolean applyingSession = false;

    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(400));

    private final MoodTrackLoader trackLoader = new MoodTrackLoader(
            musicService, sessionConfig, () -> manager.save(sessionConfig),
            () -> moodPanelController.activeMood(), this::refreshMoodState);

    private final PlaylistManager playlistManager = new PlaylistManager();
    private final NowPlayingModel nowPlaying = new NowPlayingModel();
    private final NowPlayingTracker nowPlayingTracker = new NowPlayingTracker(
            musicService::playbackState, playlistManager::getActivePlaylistName, nowPlaying);
    private final ActiveTransport transport = new ActiveTransport(
            musicService, () -> playlistTransportController.startCurrent());

    @FXML
    public void initialize() {
        musicService.setOnPlaybackChanged(nowPlayingTracker::sync);
        youTubeBarController.attach(this);
        updateBarController.attach(this);
        sceneBarController.attach(this);
        ambianceToolbarController.attach(this);
        volumeController.attach(this);
        liveAuraController.attach(this);
        moodPanelController.attach(this);
        libraryPanelController.attach(this);
        previewPanelController.attach(this);
        onDemandPanelController.attach(this);
        playlistPanelController.attach(this);
        playlistTransportController.attach(this);
        nowPlayingBarController.attach(this);
        nowPlayingTracker.sync();
        liveAuraController.refresh();

        saveDebounce.setOnFinished(e -> manager.save(sessionConfig));

        ambiance.setAudioSource(musicService::getAudioFeatures);

        if (modeTabPane != null) {
            modeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (applyingSession || newTab == null) return;
                boolean isPlaylist = newTab == playlistsTab;
                sessionConfig.setActiveMode(isPlaylist ? "playlist" : "mood");
                manager.save(sessionConfig);
                if (isPlaylist) playlistPanelController.refresh();
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
        if (shouldStartPlaylist() && playlistTransportController.startCurrent()) return;
        transport.togglePlayPause();
    }

    private boolean shouldStartPlaylist() {
        return modeTabPane.getSelectionModel().getSelectedItem() == playlistsTab
                && musicService.activeSource() == NowPlayingSource.MOOD
                && !musicService.isPlaying();
    }

    @Override
    public void togglePlayback() {
        toggleLastUsedPlayer();
    }

    @Override
    public void stopPlayback() {
        boolean wasPreview = musicService.previewSoundedLast();
        transport.stop();
        if (wasPreview) previewPanelController.hide();
    }

    @Override
    public void playPrevious() {
        playlistTransportController.previous();
    }

    @Override
    public void playNext() {
        playlistTransportController.next();
    }

    @Override
    public void seekActive(double fraction) {
        transport.seek(fraction);
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
        previewPanelController.hide();
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
        if (mode != MusicService.PlaybackMode.ON_DEMAND) onDemandPanelController.show(false);
        ambiance.activateSlot(liveSlot());
        liveAuraController.refresh();
    }

    @Override
    public void playNow(File file, String title) {
        if (file == null || !file.isFile()) return;
        String label = DisplayNames.trackLabel(file, title);
        musicService.playOnDemand(file, label, () -> {
            onDemandPanelController.show(true);
            ambiance.activateSlot(AuraSlot.ON_DEMAND);
            liveAuraController.refresh();
        });
    }

    @Override
    public void closeOnDemand() {
        enterMode(MusicService.PlaybackMode.MOOD);
    }

    @Override
    public void startPreview(File file) {
        previewPanelController.start(file);
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
        trackLoader.loadAssignments(scene);

        ambiance.applyScene(scene);

        refreshCards();
        moodPanelController.refreshButtonStates();
    }

    private void refreshMoodState() {
        refreshCards();
        moodPanelController.refreshButtonStates();
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
        trackLoader.assign(type, file, () -> {
            refreshCards();
            if (sceneEditor != null) sceneEditor.refreshTracks();
        });
    }

    @Override
    public void clearTrack(TrackType type) {
        if (type == null) return;
        trackLoader.clear(type);
        moodPanelController.refreshButtonStates();
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

    @Override
    public void playPlaylistTrack(File file) {
        playlistTransportController.play(file);
    }

    @Override
    public Optional<String> promptNewPlaylistName() {
        return playlistPanelController.promptNewName();
    }

    @Override
    public void addToPlaylist(String playlistName, File source) {
        playlistPanelController.addLibraryItem(playlistName, source);
    }

    @Override
    public void createPlaylistFromFolder(File folder) {
        playlistPanelController.createFromFolder(folder);
    }

    @Override
    public String selectedPlaylistName() {
        return playlistPanelController.selectedPlaylistName();
    }

    @Override
    public void showPendingPlaylistEntry(String text) {
        playlistPanelController.showPendingEntry(text);
    }

    @Override
    public void addPlaylistItem(String playlistName, PlaylistItem item) {
        playlistPanelController.addItem(playlistName, item);
    }

    @Override
    public void refreshPlaylistView() {
        playlistPanelController.refresh();
    }

    @Override
    public PlaylistManager playlistManager() {
        return playlistManager;
    }

    @Override
    public List<String> playlistNames() {
        return playlistManager.getPlaylists().stream().map(Playlist::getName).toList();
    }

    @Override
    public Optional<File> selectedLibraryItem() {
        return libraryPanelController.selectedItem();
    }

    @Override
    public NowPlayingModel nowPlaying() {
        return nowPlaying;
    }

    @Override
    public void persistPlaylists() {
        sessionConfig.setPlaylists(playlistManager.getPlaylists());
        manager.save(sessionConfig);
    }

    public void applySession(SessionConfig cfg) {
        if (cfg == null) return;
        sessionConfig.adoptScenesFrom(cfg);

        String lastImgDir = cfg.getLastImageFolderPath();
        if (lastImgDir != null) {
            sessionConfig.setLastImageFolderPath(lastImgDir);
        }

        int screenIdx = cfg.getAmbianceScreenIndex();
        sessionConfig.setAmbianceScreenIndex(screenIdx);
        Platform.runLater(() -> ambianceToolbarController.restoreScreen(screenIdx));

        libraryPanelController.restoreFolder(cfg.getLastFolderPath());

        playlistManager.setPlaylists(cfg.getPlaylists());
        sessionConfig.setPlaylists(cfg.getPlaylists());
        sessionConfig.setActiveMode(cfg.getActiveMode());
        sessionConfig.setActivePlaylistName(cfg.getActivePlaylistName());
        playlistPanelController.refresh();

        if ("playlist".equalsIgnoreCase(cfg.getActiveMode())) {
            applyingSession = true;
            modeTabPane.getSelectionModel().select(playlistsTab);
            String savedPlaylist = cfg.getActivePlaylistName();
            if (savedPlaylist != null) {
                playlistManager.setActivePlaylist(savedPlaylist);
                playlistPanelController.selectPlaylist(savedPlaylist);
            }
            playlistPanelController.refresh();
            applyingSession = false;
        }

        Platform.runLater(() -> {
            volumeController.restore(cfg.getMasterVolume());
            sceneBarController.refresh();
            applyScene(sessionConfig.getActiveScene());
        });
    }

    // --- Controles de ventana ---

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
