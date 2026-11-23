package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.stage.Window;

public interface PlayerHost {

    SessionConfig session();

    void saveSession();

    MusicService musicService();

    AmbianceCoordinator ambiance();

    Window window();

    void shutdownApp();

    void applyActiveScene();

    void openSceneEditor();
}
