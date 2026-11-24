package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.PlayQueue;
import com.marianocarrizo.dynamusic.service.PlaybackOrder;
import com.marianocarrizo.dynamusic.service.PlaylistManager;
import com.marianocarrizo.dynamusic.tags.TrackTagStore;
import javafx.application.Platform;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PlaylistPlayback implements QueuePanelController.Host {

    private final MusicService musicService;
    private final PlaylistManager playlistManager;
    private final PlayQueue queue = new PlayQueue();
    private final PlaybackOrder order;
    private final Consumer<MusicService.PlaybackMode> enterMode;
    private final Runnable refreshView;

    PlaylistPlayback(MusicService musicService, PlaylistManager playlistManager,
                     Consumer<MusicService.PlaybackMode> enterMode, Runnable refreshView,
                     BooleanSupplier queueEnabled) {
        this.musicService = musicService;
        this.playlistManager = playlistManager;
        this.order = new PlaybackOrder(queue, playlistManager, queueEnabled);
        this.enterMode = enterMode;
        this.refreshView = refreshView;
        musicService.setOnPlaylistEnd(() -> Platform.runLater(this::advanceAfterEnd));
        musicService.setOnPlaylistTrackFailed(() -> Platform.runLater(this::skipFailedTrack));
        musicService.setOnOnDemandEnd(() -> Platform.runLater(this::continueAfterStandalone));
    }

    @Override
    public PlayQueue queue() {
        return queue;
    }

    @Override
    public TrackTagStore tags() {
        return musicService.tags();
    }

    void enqueue(List<File> files, QueuePlacement placement) {
        placement.apply(queue, files);
    }

    void play(File file) {
        order.playing(file);
        begin(file);
    }

    void playContext(String label, List<File> tracks, int start) {
        playlistManager.playTransient(label, tracks, start);
        startCurrent();
        refreshView.run();
    }

    private void begin(File file) {
        enterMode.accept(MusicService.PlaybackMode.PLAYLIST);
        playAndPrefetch(file);
    }

    private void skipAndPrefetch(File file) {
        musicService.skipToPlaylistTrack(file);
        order.peekNext().ifPresent(musicService::prefetch);
    }

    private void playAndPrefetch(File file) {
        musicService.playPlaylistTrack(file);
        order.peekNext().ifPresent(musicService::prefetch);
    }

    boolean startCurrent() {
        Optional<File> current = order.startCurrent();
        current.ifPresent(this::begin);
        return current.isPresent();
    }

    void previous() {
        order.previous().ifPresent(this::begin);
        refreshView.run();
    }

    @Override
    public void next() {
        order.next().ifPresentOrElse(this::begin, musicService::stopPlaylist);
        refreshView.run();
    }

    private void skipFailedTrack() {
        order.next().ifPresentOrElse(this::skipAndPrefetch, musicService::stopPlaylist);
        refreshView.run();
    }

    private void advanceAfterEnd() {
        order.afterEnd().ifPresentOrElse(this::playAndPrefetch, musicService::stopPlaylist);
        refreshView.run();
    }

    private void continueAfterStandalone() {
        order.afterStandalone().ifPresent(this::begin);
        refreshView.run();
    }
}
