package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.model.AuraSlot;

import java.io.File;

public interface SceneHost {

    SessionConfig session();

    void saveSession();

    void onSceneRenamed();

    boolean deleteActiveScene();

    void onAuraEdited(AuraSlot slot);

    void assignTrack(TrackType type, File file);

    void clearTrack(TrackType type);

    void applyBackground();

    void applyFitMode();

    boolean isFfmpegAvailable();
}
