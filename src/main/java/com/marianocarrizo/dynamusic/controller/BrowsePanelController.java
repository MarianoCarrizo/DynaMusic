package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.library.Artist;
import com.marianocarrizo.dynamusic.library.IndexProgress;
import com.marianocarrizo.dynamusic.library.LibraryIndex;
import com.marianocarrizo.dynamusic.library.LibrarySnapshot;
import com.marianocarrizo.dynamusic.library.StandardLibraryIndex;
import com.marianocarrizo.dynamusic.library.TrackEntry;
import com.marianocarrizo.dynamusic.model.AppMode;
import com.marianocarrizo.dynamusic.service.CoverThumbnails;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.util.List;

public final class BrowsePanelController {

    interface Host extends FileTrackMenuItems.Host {
        void playContext(String label, List<File> tracks, int start);
    }

    @FXML private Button btnBack;
    @FXML private ToggleGroup sectionGroup;
    @FXML private ToggleButton btnAlbums;
    @FXML private ToggleButton btnArtists;
    @FXML private Label lblContext;
    @FXML private ProgressIndicator indexingIndicator;
    @FXML private Label lblStatus;
    @FXML private Label lblPlaceholder;
    @FXML private StackPane stage;

    private final BrowseNavigator navigator = new BrowseNavigator();
    private final BrowseIndexTrigger trigger = new BrowseIndexTrigger();
    private final CoverThumbnails covers = new CoverThumbnails();
    private Host host;
    private ReadOnlyObjectProperty<File> root;
    private ReadOnlyObjectProperty<AppMode> mode;
    private LibraryIndex index;
    private AlbumGridView albumGrid;
    private AlbumGridView artistAlbums;
    private ArtistListView artistList;
    private AlbumDetailView detail;
    private LibrarySnapshot snapshot = LibrarySnapshot.EMPTY;
    private IndexProgress progress = IndexProgress.IDLE;

    void attach(Host host, ReadOnlyObjectProperty<File> root, ReadOnlyObjectProperty<AppMode> mode) {
        this.host = host;
        this.root = root;
        this.mode = mode;
        AlbumActions actions = actions();
        albumGrid = new AlbumGridView(actions, covers);
        artistAlbums = new AlbumGridView(actions, covers);
        artistList = new ArtistListView(this::openArtist);
        detail = new AlbumDetailView(actions, host, covers);
        stage.getChildren().addAll(albumGrid, artistList, artistAlbums, detail);
        sectionGroup.selectedToggleProperty().addListener((obs, previous, chosen) -> onSectionChosen(previous, chosen));
        index = StandardLibraryIndex.create();
        index.onSnapshot(this::onSnapshot);
        index.onProgress(this::onProgress);
        mode.addListener((obs, previous, current) -> startIndexIfDue());
        root.addListener((obs, previous, current) -> startIndexIfDue());
        startIndexIfDue();
        refreshView();
    }

    @FXML
    private void onBack() {
        navigator.back();
        refreshView();
    }

    private void onSectionChosen(Toggle previous, Toggle chosen) {
        if (chosen == null) {
            sectionGroup.selectToggle(previous);
            return;
        }
        navigator.showSection(chosen == btnArtists ? BrowseSection.ARTISTS : BrowseSection.ALBUMS);
        refreshView();
    }

    private void startIndexIfDue() {
        trigger.due(mode.get(), root.get()).ifPresent(folder -> {
            progress = new IndexProgress(0, 0, false);
            index.open(folder.toPath());
            refreshView();
        });
    }

    private void onSnapshot(LibrarySnapshot latest) {
        snapshot = latest;
        navigator.refresh(latest);
        albumGrid.setAlbums(latest.albums());
        artistList.getItems().setAll(latest.artists());
        refreshView();
    }

    private void onProgress(IndexProgress latest) {
        progress = latest;
        refreshChrome();
    }

    private void openArtist(Artist artist) {
        navigator.openArtist(artist);
        refreshView();
    }

    private void refreshView() {
        BrowseNavigator.Page page = navigator.page();
        if (page == BrowseNavigator.Page.ARTIST_ALBUMS) artistAlbums.setAlbums(navigator.artist().albums());
        if (page == BrowseNavigator.Page.ALBUM_DETAIL) detail.show(navigator.album());
        refreshChrome();
    }

    private void refreshChrome() {
        BrowseState state = BrowseState.of(root.get() != null, !progress.done(), snapshot.trackCount());
        lblPlaceholder.setText(state.message());
        show(lblPlaceholder, state.showsPlaceholder());
        boolean ready = !state.showsPlaceholder();
        BrowseNavigator.Page page = navigator.page();
        show(albumGrid, ready && page == BrowseNavigator.Page.ALBUMS);
        show(artistList, ready && page == BrowseNavigator.Page.ARTISTS);
        show(artistAlbums, ready && page == BrowseNavigator.Page.ARTIST_ALBUMS);
        show(detail, ready && page == BrowseNavigator.Page.ALBUM_DETAIL);
        show(btnBack, navigator.canGoBack());
        lblContext.setText(contextTitle(page));
        show(indexingIndicator, !progress.done());
        lblStatus.setText(statusText());
    }

    private String contextTitle(BrowseNavigator.Page page) {
        return switch (page) {
            case ARTIST_ALBUMS -> navigator.artist().name();
            case ALBUM_DETAIL -> navigator.album().title();
            case ALBUMS, ARTISTS -> "";
        };
    }

    private String statusText() {
        if (!progress.done()) return progress.filesSeen() + " files";
        int albums = snapshot.albums().size();
        return albums == 0 ? "" : albums + (albums == 1 ? " album" : " albums");
    }

    private static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private static List<File> filesOf(Album album) {
        return album.tracks().stream().map(TrackEntry::path).map(File::new).toList();
    }

    private AlbumActions actions() {
        return new AlbumActions() {
            @Override public void open(Album album) {
                navigator.openAlbum(album);
                refreshView();
            }
            @Override public void play(Album album, int start) {
                host.playContext(album.title(), filesOf(album), start);
            }
            @Override public void enqueue(Album album, QueuePlacement placement) {
                host.enqueue(filesOf(album), placement);
            }
        };
    }
}
