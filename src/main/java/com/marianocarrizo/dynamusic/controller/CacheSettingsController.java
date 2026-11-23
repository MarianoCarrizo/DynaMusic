package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.audio.cache.AudioCache;
import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.stage.Window;

public final class CacheSettingsController {

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

    void applySavedLimit() {
        applyLimit(host.session().getAudioCacheLimit(), () -> { });
    }

    @FXML
    private void onOpen() {
        AudioCache cache = host.musicService().audio().cache();
        CacheSettingsDialog.show(host.window(), host.session().getAudioCacheLimit(), cache::usedBytes,
                this::chooseLimit, this::clear);
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
