package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlaying;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PlaylistPanelController {

    interface Host {
        SessionConfig session();

        void saveSession();

        PlaylistManager playlistManager();

        NowPlayingModel nowPlaying();

        Optional<File> selectedLibraryItem();

        void playPlaylistTrack(File file);

        void persistPlaylists();
    }


    private static final String DEFAULT_PLAYLIST_NAME = "New playlist";
    private static final String NAME_REJECTED_MESSAGE = "Name already exists or is blank.";

    @FXML private ComboBox<String> cmbPlaylist;
    @FXML private ListView<PlaylistRow> lstPlaylistTracks;
    @FXML private Label lblTrackCounter;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "playlist-io");
        t.setDaemon(true);
        return t;
    });
    private Host host;
    private PlaylistManager playlistManager;
    private boolean refreshing = false;
    private final DragActivity dragActivity = new DragActivity();
    private File followedTrack;

    void attach(Host host) {
        this.host = host;
        this.playlistManager = host.playlistManager();
        PlaylistTrackCell.Host trackHost = trackHost();
        cmbPlaylist.setOnAction(e -> {
            String name = cmbPlaylist.getValue();
            if (name == null || refreshing) return;
            activatePlaylist(name);
            host.saveSession();
            refresh();
        });
        host.nowPlaying().snapshotProperty().addListener((obs, oldValue, newValue) -> refreshPlayingState());
        lstPlaylistTracks.setOnDragEntered(e -> dragActivity.begin());
        lstPlaylistTracks.setOnDragExited(e -> dragActivity.end());
        lstPlaylistTracks.setCellFactory(lv -> new PlaylistTrackCell(trackHost));
        PlaylistTrackCell.acceptFileDropsOnList(lstPlaylistTracks, trackHost);
        lstPlaylistTracks.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) onRemoveTrack();
        });
        lstPlaylistTracks.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int idx = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    playlistManager.startAt(idx).ifPresent(f -> {
                        host.playPlaylistTrack(f);
                        refresh();
                    });
                }
            }
        });
    }

    String selectedPlaylistName() {
        return cmbPlaylist.getValue();
    }

    void selectPlaylist(String name) {
        cmbPlaylist.setValue(name);
    }

    void showPendingEntry(String text) {
        lstPlaylistTracks.getItems().add(PlaylistRow.pending(text));
    }

    private void refreshPlayingState() {
        lstPlaylistTracks.refresh();
        lblTrackCounter.setText(playlistManager.positionLabel());
        File previous = followedTrack;
        File playing = playingPlaylistTrack();
        followedTrack = playing;
        Platform.runLater(() -> followPlayingRow(previous, playing));
    }

    private File playingPlaylistTrack() {
        NowPlaying current = host.nowPlaying().get();
        return current.source() == NowPlayingSource.PLAYLIST ? current.file() : null;
    }

    private void followPlayingRow(File previous, File playing) {
        if (dragActivity.isActive()) return;
        int index = playlistManager.currentDisplayIndex();
        RowRange visible = VisibleRows.of(lstPlaylistTracks);
        if (!PlayingRowScroll.shouldScroll(previous, playing, index, visible)) return;
        lstPlaylistTracks.scrollTo(PlayingRowScroll.topIndexFor(index, visible));
    }

    void addItem(String playlistName, PlaylistItem item) {
        playlistManager.addItem(playlistName, item);
        persist();
        refresh();
    }

    void refresh() {
        if (refreshing) return;
        refreshing = true;
        try {
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

            int selected = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
            lstPlaylistTracks.getItems().setAll(
                playlistManager.getActiveItems().stream()
                    .map(PlaylistRow::of)
                    .toList()
            );
            selectTrackRow(selected);
            lstPlaylistTracks.refresh();

            lblTrackCounter.setText(playlistManager.positionLabel());
        } finally {
            refreshing = false;
        }
    }

    Optional<String> promptNewName() {
        Optional<String> created = NamePrompt.ask("New Playlist", "Name:", "").flatMap(name -> {
            if (playlistManager.createPlaylist(name) == null) {
                new Alert(Alert.AlertType.WARNING, NAME_REJECTED_MESSAGE).showAndWait();
                return Optional.empty();
            }
            return Optional.of(name);
        });
        if (created.isPresent()) {
            activatePlaylist(created.get());
            refresh();
            persist();
        }
        return created;
    }

    void addLibraryItem(String playlistName, File source) {
        if (source == null) return;
        boolean isFolder = source.isDirectory();
        collectInBackground(() -> AudioFileCollector.collect(source), tracks -> {
            if (tracks.isEmpty()) {
                if (isFolder) showNoAudioFilesMessage();
                return;
            }
            addTracks(playlistName, tracks, Integer.MAX_VALUE, false);
        });
    }

    void createFromFolder(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        collectInBackground(() -> AudioFileCollector.collect(folder), tracks -> {
            if (tracks.isEmpty()) {
                showNoAudioFilesMessage();
                return;
            }
            String name = playlistManager.ensurePlaylist(playlistNameFor(folder)).getName();
            addTracks(name, tracks, Integer.MAX_VALUE, true);
        });
    }

    @FXML
    private void onNewPlaylist() {
        promptNewName();
    }

    @FXML
    private void onRenamePlaylist() {
        String current = cmbPlaylist.getValue();
        if (current == null) return;
        NamePrompt.ask("Rename", "New name:", current).ifPresent(name -> {
            if (!playlistManager.renamePlaylist(current, name)) {
                new Alert(Alert.AlertType.WARNING, NAME_REJECTED_MESSAGE).showAndWait();
                return;
            }
            refresh();
            persist();
        });
    }

    @FXML
    private void onDeletePlaylist() {
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
        refresh();
        persist();
    }

    @FXML
    private void onAddToPlaylist() {
        String name = cmbPlaylist.getValue();
        if (name == null) return;
        host.selectedLibraryItem().ifPresent(file -> addLibraryItem(name, file));
    }

    @FXML
    private void onRemoveTrack() {
        removeTrackAt(lstPlaylistTracks.getSelectionModel().getSelectedIndex());
    }

    private PlaylistTrackCell.Host trackHost() {
        return new PlaylistTrackCell.Host() {
            @Override public RowPlayback playbackOf(int index) {
                NowPlaying current = host.nowPlaying().get();
                boolean currentRow = current.source() == NowPlayingSource.PLAYLIST
                        && index == playlistManager.currentDisplayIndex();
                return RowPlayback.of(currentRow, current.playing());
            }
            @Override public void filesDropped(List<File> files, int slot) {
                addDroppedFiles(files, slot);
            }
            @Override public void trackMoved(int from, int slot) {
                moveTrackToSlot(from, slot);
            }
            @Override public void removeRequested(int index) {
                removeTrackAt(index);
            }
            @Override public void rowDragStarted() {
                dragActivity.begin();
            }
            @Override public void rowDragFinished() {
                dragActivity.end();
            }
        };
    }

    private void addDroppedFiles(List<File> dropped, int slot) {
        collectInBackground(() -> AudioFileCollector.collectAll(dropped), tracks -> {
            if (tracks.isEmpty()) return;
            String target = playlistManager.getActivePlaylistName();
            if (target == null) target = playlistManager.ensurePlaylist(DEFAULT_PLAYLIST_NAME).getName();
            addTracks(target, tracks, slot, false);
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

    private void addTracks(String playlistName, List<File> tracks, int slot, boolean activate) {
        String activeBefore = playlistManager.getActivePlaylistName();
        boolean isActive = playlistName.equalsIgnoreCase(activeBefore);
        int selected = lstPlaylistTracks.getSelectionModel().getSelectedIndex();
        int added = playlistManager.insertTracks(playlistName, tracks, slot);
        if (activate || activeBefore == null) {
            activatePlaylist(playlistName);
            if (!isActive) selected = -1;
        }
        if (isActive && slot <= selected) selected += added;
        refresh();
        selectTrackRow(selected);
        persist();
    }

    private void selectTrackRow(int index) {
        if (index < 0 || index >= lstPlaylistTracks.getItems().size()) return;
        lstPlaylistTracks.getSelectionModel().select(index);
    }

    private void activatePlaylist(String name) {
        playlistManager.setActivePlaylist(name);
        host.session().setActivePlaylistName(name);
    }

    private void moveTrackToSlot(int from, int slot) {
        String active = playlistManager.getActivePlaylistName();
        if (active == null) return;
        int to = slot > from ? slot - 1 : slot;
        if (to == from) return;
        playlistManager.reorderTrack(active, from, to);
        refresh();
        lstPlaylistTracks.getSelectionModel().select(to);
        persist();
    }

    private void removeTrackAt(int index) {
        String active = playlistManager.getActivePlaylistName();
        if (active == null || index < 0) return;
        playlistManager.removeTrack(active, index);
        refresh();
        selectTrackRow(Math.min(index, lstPlaylistTracks.getItems().size() - 1));
        persist();
    }

    private void persist() {
        host.persistPlaylists();
    }
}
