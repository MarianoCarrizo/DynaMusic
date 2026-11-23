package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.stage.Window;

import java.io.File;

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
}
