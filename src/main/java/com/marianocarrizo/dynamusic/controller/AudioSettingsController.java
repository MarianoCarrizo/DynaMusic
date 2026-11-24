package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.audio.cache.AudioCache;
import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.stage.Window;

public final class AudioSettingsController {

    interface Host {
        SessionConfig session();

        void saveSession();

        MusicService musicService();

        Window window();
    }

    private Host host;

    void attach(Host host) {
        this.host = host;
    }

    void applySaved() {
        host.musicService().setStreamingEngineEnabled(host.session().isStreamingEngineEnabled());
        applyLimit(host.session().getAudioCacheLimit(), () -> { });
    }

    @FXML
    private void onOpen() {
        AudioCache cache = host.musicService().audio().cache();
        AudioSettingsDialog.show(host.window(), host.session().getAudioCacheLimit(), cache::usedBytes,
                this::chooseLimit, this::clear,
                StreamingEngineSection.create(host.session().isStreamingEngineEnabled(), this::toggleStreaming));
    }

    private void toggleStreaming(boolean enabled) {
        host.session().setStreamingEngineEnabled(enabled);
        host.saveSession();
        host.musicService().setStreamingEngineEnabled(enabled);
    }

    private void chooseLimit(CacheLimit limit, Runnable afterwards) {
        host.session().setAudioCacheLimit(limit);
        host.saveSession();
        applyLimit(limit, afterwards);
    }

    private void applyLimit(CacheLimit limit, Runnable afterwards) {
        AudioCache cache = host.musicService().audio().cache();
        host.musicService().audio().maintenance().execute(() -> {
            cache.setLimit(limit);
            Platform.runLater(afterwards);
        });
    }

    private void clear(Runnable afterwards) {
        AudioCache cache = host.musicService().audio().cache();
        host.musicService().audio().maintenance().execute(() -> {
            cache.clear();
            Platform.runLater(afterwards);
        });
    }
}
