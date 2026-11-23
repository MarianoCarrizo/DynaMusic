package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.ambiance.AmbianceStage;
import com.marianocarrizo.dynamusic.ambiance.BackgroundFitMode;
import com.marianocarrizo.dynamusic.ambiance.BoomerangBaker;
import com.marianocarrizo.dynamusic.ambiance.TransitionStyle;
import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.model.AuraConfig;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.BackgroundConfig;
import com.marianocarrizo.dynamusic.model.BackgroundResolver;
import com.marianocarrizo.dynamusic.model.Scene;
import com.marianocarrizo.dynamusic.service.AudioFeatures;
import javafx.application.Platform;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AmbianceCoordinator {

    private final AmbianceStage stage = new AmbianceStage();
    private final BoomerangBaker boomerangBaker = new BoomerangBaker();
    private final SessionConfig session;
    private final Runnable saveSession;
    private final Supplier<AuraSlot> liveSlot;

    private boolean ffmpegAvailable = false;
    private BackgroundConfig appliedBackground;
    private String bakeGuardSrc;
    private long bakeGuardStart;
    private long bakeGuardEnd;

    public AmbianceCoordinator(SessionConfig session, Runnable saveSession, Supplier<AuraSlot> liveSlot,
                               Runnable onBackgroundDropped) {
        this.session = session;
        this.saveSession = saveSession;
        this.liveSlot = liveSlot;
        stage.setOnBackgroundDropped(path -> {
            applyDroppedBackground(path);
            Platform.runLater(onBackgroundDropped);
        });
    }

    public void open() {
        stage.open();
    }

    public void show() {
        stage.show();
    }

    public void hide() {
        stage.hide();
    }

    public boolean isVisible() {
        return stage.isVisible();
    }

    public void setOnVisibilityChanged(Consumer<Boolean> callback) {
        stage.setOnVisibilityChanged(callback);
    }

    public void applyScreen(int index) {
        stage.applyScreen(index);
    }

    public void setAudioSource(Supplier<AudioFeatures> source) {
        stage.setAudioSource(source);
    }

    public void setAura(AuraSlot slot, AuraConfig aura) {
        stage.setAura(slot, aura);
    }

    public boolean isFfmpegAvailable() {
        return ffmpegAvailable;
    }

    public void setFfmpegAvailable(boolean available) {
        this.ffmpegAvailable = available;
    }

    public void applyScene(Scene scene) {
        Map<AuraSlot, AuraConfig> auras = new EnumMap<>(AuraSlot.class);
        for (AuraSlot slot : AuraSlot.values()) auras.put(slot, scene.getAura(slot));
        stage.setAuras(auras);
        stage.setActiveSlot(liveSlot.get());
        showLiveBackground();
    }

    public void activateSlot(AuraSlot slot) {
        stage.setActiveSlot(slot);
        applyBackgroundIfChanged(slot);
    }

    public void applyBackgroundIfChanged(AuraSlot slot) {
        BackgroundConfig next = resolveBackground(slot);
        if (BackgroundResolver.needsShow(appliedBackground, next)) {
            showBackground(next, stage.activeTransitionStyle());
        }
    }

    public void applyFitMode() {
        BackgroundConfig live = resolveBackground(liveSlot.get());
        stage.setBackgroundFitMode(BackgroundFitMode.fromString(live.getFitMode()));
        if (appliedBackground != null) appliedBackground.setFitMode(live.getFitMode());
    }

    private void applyDroppedBackground(String path) {
        BackgroundConfig bg = currentBackground();
        bg.setPath(path);
        session.setLastImageFolderPath(new File(path).getParent());
        bg.setSegmentStartMs(null);
        bg.setSegmentEndMs(null);
        bg.setBoomerangEnabled(false);
        appliedBackground = bg.copy();
        saveSession.run();
    }

    private BackgroundConfig currentBackground() {
        return session.getActiveScene().backgroundFor(liveSlot.get());
    }

    private void showLiveBackground() {
        showBackground(resolveBackground(liveSlot.get()), TransitionStyle.NEUTRAL);
    }

    private BackgroundConfig resolveBackground(AuraSlot slot) {
        return BackgroundResolver.resolve(session.getActiveScene(), slot, this::isUsableMedia, ffmpegAvailable);
    }

    private boolean isUsableMedia(String path) {
        return !boomerangBaker.isCachePath(path) && new File(path).exists();
    }

    private void showBackground(BackgroundConfig bg, TransitionStyle style) {
        String bgPath = bg.getPath();
        Long segStart = bg.getSegmentStartMs();
        Long segEnd = bg.getSegmentEndMs();
        Double storedRate = bg.getPlaybackRate();
        double rate = storedRate != null ? storedRate : 1.0;
        BackgroundFitMode fit = BackgroundFitMode.fromString(bg.getFitMode());

        stage.resetBoomerang();
        appliedBackground = bg;

        if (bgPath == null) {
            stage.setBackground(null, style);
            stage.clearVideoSegment();
            stage.setBackgroundFitMode(fit);
            return;
        }

        stage.setBackground(bgPath, style);
        boolean isVideo = "Video".equals(BackgroundEditorController.mediaType(bgPath));
        if (isVideo && segStart != null && segEnd != null) {
            stage.setVideoSegment(Duration.millis(segStart), Duration.millis(segEnd));
        } else {
            stage.clearVideoSegment();
        }
        stage.setVideoRate(rate);
        stage.setBackgroundFitMode(fit);

        if (isVideo && Boolean.TRUE.equals(bg.getBoomerangEnabled())) {
            if (boomerangBaker.isCached(bgPath, segStart, segEnd)) {
                stage.activateBoomerang(boomerangBaker.cacheKeyPath(bgPath, segStart, segEnd).toString());
            } else {
                startBake(bgPath, segStart, segEnd);
            }
        }
    }

    private void startBake(String src, long start, long end) {
        bakeGuardSrc = src;
        bakeGuardStart = start;
        bakeGuardEnd = end;
        boomerangBaker.bakeAsync(src, start, end,
                outPath -> Platform.runLater(() -> onBakeSuccess(outPath, src, start, end)),
                err -> Platform.runLater(() -> onBakeFailure(err, src, start, end)));
    }

    private boolean isBakeStillWanted(String src, long start, long end) {
        BackgroundConfig bg = appliedBackground;
        return bg != null
                && src.equals(bg.getPath())
                && bg.getSegmentStartMs() != null
                && start == bg.getSegmentStartMs()
                && bg.getSegmentEndMs() != null
                && end == bg.getSegmentEndMs()
                && src.equals(bakeGuardSrc)
                && start == bakeGuardStart
                && end == bakeGuardEnd;
    }

    private void onBakeSuccess(Path out, String src, long start, long end) {
        if (!isBakeStillWanted(src, start, end)) return;
        stage.activateBoomerang(out.toString());
    }

    private void onBakeFailure(String stderrTail, String src, long start, long end) {
        System.err.println(stderrTail);
        if (!isBakeStillWanted(src, start, end)) return;
        stage.deactivateBoomerang();
        appliedBackground.setBoomerangEnabled(false);
        BackgroundConfig persisted = currentBackground();
        if (src.equals(persisted.getPath())) persisted.setBoomerangEnabled(false);
        saveSession.run();
    }
}
