package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import com.marianocarrizo.dynamusic.model.AppMode;
import com.marianocarrizo.dynamusic.service.MusicService;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.control.TabPane;

final class ModeShell implements ModeSwitchController.Host {

    private final SessionConfig session;
    private final Runnable saveSession;
    private final ModeTransition transition;
    private final ObjectProperty<AppMode> mode = new SimpleObjectProperty<>(AppMode.SESSION);
    private final BooleanBinding sessionActive = mode.isEqualTo(AppMode.SESSION);
    private ModeVisibility visibility;
    private ModeTabs tabs;
    private Runnable refreshAccent;

    ModeShell(MusicService musicService, AmbianceCoordinator ambiance, SessionConfig session, Runnable saveSession) {
        this.session = session;
        this.saveSession = saveSession;
        this.transition = new ModeTransition(new PlayersAndAmbiance(musicService, ambiance));
    }

    void install(Parent root, TabPane tabPane, ModeSwitchController switchController, Runnable refreshAccent) {
        this.visibility = new ModeVisibility(root);
        this.tabs = ModeTabs.of(tabPane);
        this.refreshAccent = refreshAccent;
        switchController.attach(this);
        apply(AppMode.SESSION);
    }

    void restore() {
        AppMode saved = session.getAppMode();
        if (saved != mode.get()) enter(saved);
    }

    void whenSession(Runnable action) {
        transition.whenSession(mode.get(), action);
    }

    boolean tabsFollowSession() {
        return mode.get() == AppMode.SESSION && !tabs.isSwapping();
    }

    boolean isListen() {
        return mode.get() == AppMode.LISTEN;
    }

    BooleanBinding sessionActiveProperty() {
        return sessionActive;
    }

    @Override
    public ReadOnlyObjectProperty<AppMode> modeProperty() {
        return mode;
    }

    @Override
    public void request(AppMode target) {
        if (target == null || target == mode.get()) return;
        session.setAppMode(target);
        saveSession.run();
        enter(target);
    }

    private void enter(AppMode target) {
        mode.set(target);
        if (target == AppMode.LISTEN) transition.toListen();
        else transition.toSession();
        apply(target);
    }

    private void apply(AppMode target) {
        visibility.apply(target);
        tabs.show(target);
        refreshAccent.run();
    }

    private static final class PlayersAndAmbiance implements ModeTransition.Host {

        private final MusicService musicService;
        private final AmbianceCoordinator ambiance;

        PlayersAndAmbiance(MusicService musicService, AmbianceCoordinator ambiance) {
            this.musicService = musicService;
            this.ambiance = ambiance;
        }

        @Override
        public boolean moodPlaying() {
            return musicService.getPlaybackMode() == MusicService.PlaybackMode.MOOD && musicService.isPlaying();
        }

        @Override
        public void pauseMood() {
            musicService.fadeOutAndPause(null);
        }

        @Override
        public boolean ambianceVisible() {
            return ambiance.isVisible();
        }

        @Override
        public void hideAmbiance() {
            ambiance.hide();
        }

        @Override
        public void showAmbiance() {
            ambiance.show();
        }
    }
}
