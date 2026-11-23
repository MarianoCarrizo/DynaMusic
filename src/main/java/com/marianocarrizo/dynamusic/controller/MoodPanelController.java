package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.AuraConfig;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.Scene;
import com.marianocarrizo.dynamusic.component.SeekBar;
import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public final class MoodPanelController {

    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");
    private static final String PLAY_ICON = "▶";
    private static final String PAUSE_ICON = "⏸";

    @FXML private Button btnPaused;
    @FXML private Button btnNormal;
    @FXML private Button btnRaged;
    @FXML private Button btnAdvantage;
    @FXML private ToggleButton btnLoopIcon;
    @FXML private Button btnPlayPause;
    @FXML private SeekBar moodSeekBar;
    @FXML private Label lblNowPlayingMood;
    @FXML private Label lblNowPlaying;

    private final Map<TrackType, Button> moodButtons = new EnumMap<>(TrackType.class);
    private final Map<TrackType, MoodCard> moodCards = new EnumMap<>(TrackType.class);
    private PlayerHost host;
    private TrackType activeMood = TrackType.NORMAL;

    private static final class MoodCard {
        final Label track = new Label();
        final Circle dot = new Circle(5);
        final Label preset = new Label();
    }

    void attach(PlayerHost host) {
        this.host = host;
        MusicService musicService = host.musicService();
        MoodBackgroundMenu backgroundMenu = new MoodBackgroundMenu(host);
        bindCard(TrackType.PAUSED, btnPaused, 1, backgroundMenu);
        bindCard(TrackType.NORMAL, btnNormal, 2, backgroundMenu);
        bindCard(TrackType.RAGED, btnRaged, 3, backgroundMenu);
        bindCard(TrackType.ADVANTAGE, btnAdvantage, 4, backgroundMenu);
        refreshCards();
        refreshButtonStates();
        showActiveMood();

        btnLoopIcon.selectedProperty().addListener((obs, oldVal, newVal) -> musicService.setLoop(newVal));
        musicService.setProgressListener((current, total) ->
            Platform.runLater(() -> {
                moodSeekBar.showPlayback(current, total);
                refreshButtonStates();
                syncPlayPauseIcon();
                host.playbackStateChanged();
            }));
        moodSeekBar.setOnSeekToFraction(musicService::seekCurrentTrack);
        lblNowPlaying.textProperty().bind(host.nowPlaying().titleProperty());
    }

    TrackType activeMood() {
        return activeMood;
    }

    void setActiveMood(TrackType type) {
        activeMood = type;
        showActiveMood();
    }

    void refreshCards() {
        Scene scene = host.session().getActiveScene();
        Map<TrackType, String> assignments = scene.getAssignments();
        for (TrackType type : TrackType.values()) {
            MoodCard card = moodCards.get(type);
            String path = assignments.get(type);
            card.track.getStyleClass().remove("card-track-empty");
            if (path == null || path.isEmpty()) {
                card.track.setText("Drop a track here");
                card.track.getStyleClass().add("card-track-empty");
            } else {
                File f = new File(path);
                card.track.setText(DisplayNames.stripExtension(f.getName()) + (f.isFile() ? "" : " (missing)"));
            }
            AuraConfig aura = scene.getAura(AuraSlot.of(type));
            card.dot.setFill(Color.web(aura.getPrimaryColor()));
            card.preset.setText(DisplayNames.capitalize(aura.getPreset().name()));
        }
    }

    void refreshButtonStates() {
        MusicService musicService = host.musicService();
        for (TrackType t : TrackType.values()) {
            Button b = moodButtons.get(t);
            b.getStyleClass().remove("btn-phase-empty");
            b.getStyleClass().remove("btn-phase-active");
            if (!musicService.isLoaded(t)) b.getStyleClass().add("btn-phase-empty");
        }
        if (activeMood != null && musicService.isLoaded(activeMood)) {
            moodButtons.get(activeMood).getStyleClass().add("btn-phase-active");
        }
    }

    void showPlaying(boolean playing) {
        btnPlayPause.setText(playing ? PAUSE_ICON : PLAY_ICON);
    }

    void syncPlayPauseIcon() {
        showPlaying(host.musicService().isPlaying());
    }

    void resetProgressIfEmpty() {
        if (host.musicService().isLoaded(activeMood)) return;
        moodSeekBar.showNoTrack();
    }

    @FXML private void onSwitchToPaused() { host.switchTrack(TrackType.PAUSED); }
    @FXML private void onSwitchToNormal() { host.switchTrack(TrackType.NORMAL); }
    @FXML private void onSwitchToRaged() { host.switchTrack(TrackType.RAGED); }
    @FXML private void onSwitchToAdvantage() { host.switchTrack(TrackType.ADVANTAGE); }

    @FXML
    void togglePlayPause() {
        MusicService musicService = host.musicService();
        if (musicService.isPlaying()) {
            showPlaying(false);
            musicService.fadeOutAndPause(null);
        } else {
            showPlaying(true);
            host.enterMode(MusicService.PlaybackMode.MOOD);
            musicService.fadeInAndPlay();
        }
        host.playbackStateChanged();
    }

    @FXML
    private void onStop() {
        host.musicService().stop();
        showPlaying(false);
        host.playbackStateChanged();
    }

    @FXML
    private void onToggleLoop() {
        host.musicService().setLoop(btnLoopIcon.isSelected());
    }

    private void showActiveMood() {
        lblNowPlayingMood.setText("NOW PLAYING · " + activeMood.name());
    }

    private void bindCard(TrackType type, Button button, int number, MoodBackgroundMenu backgroundMenu) {
        MoodCard card = new MoodCard();

        Label title = new Label("[" + number + "] " + type.name());
        title.getStyleClass().add("card-title");
        card.track.getStyleClass().add("card-track");
        card.track.setMaxWidth(190);
        card.track.setAlignment(Pos.CENTER);
        card.preset.getStyleClass().add("card-aura");
        HBox auraRow = new HBox(5, card.dot, card.preset);
        auraRow.setAlignment(Pos.CENTER);

        VBox box = new VBox(4, title, card.track, auraRow);
        box.setAlignment(Pos.CENTER);
        button.setText(null);
        button.setGraphic(box);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setMinWidth(0);

        button.setOnDragOver(e -> {
            if (audioFileIn(e.getDragboard()) != null) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        button.setOnDragEntered(e -> {
            if (audioFileIn(e.getDragboard()) != null) button.pseudoClassStateChanged(DROP_TARGET, true);
        });
        button.setOnDragExited(e -> button.pseudoClassStateChanged(DROP_TARGET, false));
        button.setOnDragDropped(e -> {
            File f = audioFileIn(e.getDragboard());
            if (f != null) host.assignTrack(type, f);
            e.setDropCompleted(f != null);
            e.consume();
        });

        button.setContextMenu(backgroundMenu.forSlot(AuraSlot.of(type)));

        moodButtons.put(type, button);
        moodCards.put(type, card);
    }

    private File audioFileIn(Dragboard db) {
        if (db == null || !db.hasFiles()) return null;
        for (File f : db.getFiles()) {
            if (f.isFile() && AudioFileCollector.isAudioFile(f)) return f;
        }
        return null;
    }
}
