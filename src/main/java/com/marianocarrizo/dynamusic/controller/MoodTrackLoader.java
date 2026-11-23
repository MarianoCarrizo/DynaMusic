package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.Scene;
import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import com.marianocarrizo.dynamusic.service.MusicService;
import com.marianocarrizo.dynamusic.service.TrackLibrary;
import javafx.application.Platform;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

final class MoodTrackLoader {

    private final MusicService musicService;
    private final SessionConfig session;
    private final Runnable saveSession;
    private final Supplier<TrackType> activeMood;
    private final Runnable onLoadSettled;
    private final Map<TrackType, String> loadedPaths = new EnumMap<>(TrackType.class);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "track-io");
        t.setDaemon(true);
        return t;
    });

    MoodTrackLoader(MusicService musicService, SessionConfig session, Runnable saveSession,
                    Supplier<TrackType> activeMood, Runnable onLoadSettled) {
        this.musicService = musicService;
        this.session = session;
        this.saveSession = saveSession;
        this.activeMood = activeMood;
        this.onLoadSettled = onLoadSettled;
    }

    void loadAssignments(Scene scene) {
        Map<TrackType, String> assignments = scene.getAssignments();
        for (TrackType t : TrackType.values()) {
            String path = assignments.get(t);
            if (path != null && new File(path).isFile()) {
                if (!path.equals(loadedPaths.get(t))) load(t, path);
            } else {
                unload(t);
            }
        }
    }

    void assign(TrackType type, File file, Runnable onAssigned) {
        if (type == null || file == null || !file.isFile() || !AudioFileCollector.isAudioFile(file)) return;
        final Scene target = session.getActiveScene();
        final String source = file.getAbsolutePath();
        CompletableFuture.supplyAsync(() -> TrackLibrary.pin(source), ioExecutor)
            .whenComplete((pinned, err) -> Platform.runLater(() -> {
                String path = (err == null && pinned != null) ? pinned : source;
                target.setAssignment(type, path);
                saveSession.run();
                if (target == session.getActiveScene()) {
                    load(type, path);
                    onAssigned.run();
                }
            }));
    }

    void clear(TrackType type) {
        session.getActiveScene().setAssignment(type, null);
        saveSession.run();
        unload(type);
    }

    private void load(TrackType type, String path) {
        loadedPaths.put(type, path);
        musicService.loadTrack(type, new File(path), () -> onTrackLoaded(type), () -> onTrackLoadFailed(type, path));
    }

    private void unload(TrackType type) {
        loadedPaths.remove(type);
        musicService.unloadTrack(type);
    }

    private void onTrackLoadFailed(TrackType type, String path) {
        if (!path.equals(loadedPaths.get(type))) return;
        unload(type);
        onLoadSettled.run();
    }

    private void onTrackLoaded(TrackType type) {
        if (type == activeMood.get() && !musicService.isPlaying()
                && musicService.getCurrentTrack() != type) {
            musicService.selectTrack(type);
        }
        onLoadSettled.run();
    }
}
