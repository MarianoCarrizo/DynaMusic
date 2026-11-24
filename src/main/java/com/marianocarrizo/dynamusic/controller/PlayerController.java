package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.config.SessionConfigManager;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.RepeatMode;
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
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class PlayerController implements SceneHost, UpdateBarController.Host, SceneBarController.Host,
        AmbianceToolbarController.Host, LiveAuraController.Host, MoodPanelController.Host,
        LibraryPanelController.Host, PlaylistPanelController.Host, YouTubeBarController.Host,
        NowPlayingBarController.Host, AudioSettingsController.Host {

    private static final String CAFECITO_URL = "https://cafecito.app/duhonha";
    private HostServices hostServices;

    // Layout
    @FXML private VBox mainRoot;
    @FXML private Button btnMaximize;
    @FXML private SplitPane mainSplit;
    @FXML private VBox leftPanel;
    @FXML private ToggleButton btnToggleLibrary;

    @FXML private UpdateBarController updateBarController;
    @FXML private SceneBarController sceneBarController;
    @FXML private AmbianceToolbarController ambianceToolbarController;
    @FXML private AudioSettingsController audioSettingsController;

    @FXML private MoodPanelController moodPanelController;
    @FXML private LiveAuraController liveAuraController;

    @FXML private NowPlayingBarController nowPlayingBarController;

    // T-10: TabPane de modos
    @FXML private TabPane modeTabPane;
    @FXML private Tab moodsTab;
    @FXML private Tab playlistsTab;

    @FXML private PlaylistPanelController playlistPanelController;

    @FXML private LibraryPanelController libraryPanelController;
    @FXML private YouTubeBarController youTubeBarController;

    private final MusicService musicService = new MusicService();
    private final SessionConfigManager manager = new SessionConfigManager();
    private final SessionConfig sessionConfig = new SessionConfig();
    private final AmbianceCoordinator ambiance = new AmbianceCoordinator(
            sessionConfig, () -> manager.save(sessionConfig), this::liveSlot, this::refreshSceneEditorBackground);
    private SceneEditorController sceneEditor;
    private LibraryVisibility libraryVisibility;

    private boolean applyingSession = false;

    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(400));

    private final MoodTrackLoader trackLoader = new MoodTrackLoader(
            musicService, sessionConfig, () -> manager.save(sessionConfig),
            () -> moodPanelController.activeMood(), this::refreshMoodState);

    private final PlaylistManager playlistManager = new PlaylistManager();
    private final NowPlayingModel nowPlaying = new NowPlayingModel();
    private final NowPlayingTracker nowPlayingTracker = new NowPlayingTracker(
            musicService::playbackState, playlistManager::getActivePlaylistName, nowPlaying);
    private final PlaylistPlayback playlistPlayback = new PlaylistPlayback(
            musicService, playlistManager, this::enterMode, this::refreshPlaylistView);
    private final ActiveTransport transport = new ActiveTransport(musicService, playlistPlayback::startCurrent);
    private final RepeatSettings repeatSettings = new RepeatSettings(
            mode -> musicService.setLoop(mode.repeatsTrack()), playlistManager::setRepeat,
            mode -> musicService.setOnDemandLoop(mode.repeatsTrack()),
            mode -> musicService.setPreviewLoop(mode.repeatsTrack()));

    private final FileOpener fileOpener = new FileOpener(
            this::playNow, folder -> libraryPanelController.showFolder(folder),
            file -> libraryPanelController.reveal(file));

    @FXML
    public void initialize() {
        libraryVisibility = new LibraryVisibility(mainSplit, leftPanel, btnToggleLibrary,
                this::onLibraryDividerMoved);
        musicService.setOnPlaybackChanged(nowPlayingTracker::sync);
        youTubeBarController.attach(this);
        updateBarController.attach(this);
        sceneBarController.attach(this);
        ambianceToolbarController.attach(this);
        audioSettingsController.attach(this);
        liveAuraController.attach(this);
        moodPanelController.attach(this);
        libraryPanelController.attach(this);
        playlistPanelController.attach(this);
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

        WindowFileDrop.install(mainRoot, fileOpener::open);

        Platform.runLater(() -> {
            updateWindowMood(TrackType.NORMAL);
            if (sessionConfig.isAmbianceIntroduced()) ambiance.open();
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
        if (shouldStartPlaylist() && playlistPlayback.startCurrent()) return;
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
    public void playPrevious() {
        playlistPlayback.previous();
    }

    @Override
    public void playNext() {
        playlistPlayback.next();
    }

    @Override
    public boolean isShuffleOn() {
        return playlistManager.isShuffle();
    }

    @Override
    public void setShuffle(boolean on) {
        playlistManager.setShuffle(on);
        refreshPlaylistView();
    }

    @Override
    public RepeatMode repeatMode(NowPlayingSource source) {
        return repeatSettings.modeOf(source);
    }

    @Override
    public RepeatMode cycleRepeat(NowPlayingSource source) {
        RepeatMode mode = repeatSettings.cycle(source);
        sessionConfig.setRepeatMode(source, mode);
        manager.save(sessionConfig);
        return mode;
    }

    public boolean moodKeysEnabled() {
        return MoodShortcuts.enabled(modeTabPane.getSelectionModel().getSelectedItem(), moodsTab);
    }

    private void restoreRepeatModes(SessionConfig saved) {
        for (NowPlayingSource source : NowPlayingSource.values()) {
            if (!source.isRepeatable()) continue;
            repeatSettings.set(source, saved.getRepeatMode(source));
            sessionConfig.setRepeatMode(source, repeatSettings.modeOf(source));
        }
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
    }

    private boolean usesOnDemandAura() {
        MusicService.PlaybackMode mode = musicService.getPlaybackMode();
        return mode == MusicService.PlaybackMode.ON_DEMAND || mode == MusicService.PlaybackMode.PLAYLIST;
    }

    @Override
    public AuraSlot liveSlot() {
        return usesOnDemandAura() ? AuraSlot.ON_DEMAND : AuraSlot.of(moodPanelController.activeMood());
    }

    private void enterMode(MusicService.PlaybackMode target) {
        musicService.setPlaybackMode(target);
        syncSourceUi();
    }

    private void syncSourceUi() {
        ambiance.activateSlot(liveSlot());
        liveAuraController.refresh();
    }

    @Override
    public void playNow(File file, String title) {
        if (file == null || !file.isFile()) return;
        String label = DisplayNames.trackLabel(file, title);
        musicService.playOnDemand(file, label, () -> {
            ambiance.activateSlot(AuraSlot.ON_DEMAND);
            liveAuraController.refresh();
        });
    }

    public void openFiles(List<File> files) {
        fileOpener.open(files);
    }

    @Override
    public void focusYoutubeUrl() {
        youTubeBarController.focusUrl();
    }

    @Override
    public void startPreview(File file) {
        musicService.previewTrack(file);
    }

    private void updateWindowMood(TrackType type) {
        if (type == null || mainRoot == null) return;
        mainRoot.setId(switch (type) {
            case PAUSED    -> "window-paused";
            case RAGED     -> "window-raged";
            case NORMAL    -> "window-normal";
            case ADVANTAGE -> "window-advantage";
        });
        if (sceneEditor != null) sceneEditor.refreshAccent();
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    private void onLibraryDividerMoved(double position) {
        sessionConfig.setLibraryDividerPosition(position);
        saveDebounce.playFromStart();
    }

    @FXML
    private void onToggleLibrary() {
        libraryVisibility.set(btnToggleLibrary.isSelected());
        sessionConfig.setLibraryCollapsed(!libraryVisibility.isVisible());
        manager.save(sessionConfig);
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

    @Override public String windowMoodId() { return mainRoot.getId(); }
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
        playlistPlayback.play(file);
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
        sessionConfig.adoptAmbianceIntroductionFrom(cfg);
        sessionConfig.adoptAudioCacheLimitFrom(cfg);
        sessionConfig.adoptStreamingEngineFrom(cfg);
        audioSettingsController.applySaved();

        String lastImgDir = cfg.getLastImageFolderPath();
        if (lastImgDir != null) {
            sessionConfig.setLastImageFolderPath(lastImgDir);
        }

        int screenIdx = cfg.getAmbianceScreenIndex();
        sessionConfig.setAmbianceScreenIndex(screenIdx);
        Platform.runLater(() -> ambianceToolbarController.restoreScreen(screenIdx));

        libraryPanelController.restoreFolder(cfg.getLastFolderPath());
        sessionConfig.setLibraryCollapsed(cfg.isLibraryCollapsed());
        sessionConfig.setLibraryDividerPosition(cfg.getLibraryDividerPosition());
        libraryVisibility.setDividerPosition(cfg.getLibraryDividerPosition());
        libraryVisibility.set(!cfg.isLibraryCollapsed());

        restoreRepeatModes(cfg);
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
            nowPlayingBarController.restoreVolume(cfg.getMasterVolume());
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
