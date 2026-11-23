package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.NowPlayingModel;
import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.stage.Window;

import java.io.File;
import java.util.Optional;

public interface PlayerHost {

    SessionConfig session();

    void saveSession();

    MusicService musicService();

    AmbianceCoordinator ambiance();

    Window window();

    void shutdownApp();

    void applyActiveScene();

    void openSceneEditor();

    AuraSlot liveSlot();

    void liveAuraEdited(AuraSlot slot);

    void slotBackgroundEdited();

    void switchTrack(TrackType type);

    void enterMode(MusicService.PlaybackMode mode);

    void assignTrack(TrackType type, File file);

    void playNow(File file, String title);

    void startPreview(File file);

    void closeOnDemand();

    PlaylistManager playlistManager();

    Optional<String> promptNewPlaylistName();

    void addToPlaylist(String playlistName, File source);

    void createPlaylistFromFolder(File folder);

    String selectedPlaylistName();

    void showPendingPlaylistEntry(String text);

    void addPlaylistItem(String playlistName, PlaylistItem item);

    void refreshPlaylistView();

    void playPlaylistTrack(File file);

    Optional<File> selectedLibraryItem();

    void persistPlaylists();

    NowPlayingModel nowPlaying();

    void playbackStateChanged();
}
