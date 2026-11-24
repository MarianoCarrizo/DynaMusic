package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;

final class ModeTransition {

    interface Host {
        boolean moodPlaying();

        void pauseMood();

        boolean ambianceVisible();

        void hideAmbiance();

        void showAmbiance();
    }

    private final Host host;
    private boolean ambianceWasVisible;
    private Runnable pendingForSession;

    ModeTransition(Host host) {
        this.host = host;
    }

    void toListen() {
        if (host.moodPlaying()) host.pauseMood();
        ambianceWasVisible = host.ambianceVisible();
        if (ambianceWasVisible) host.hideAmbiance();
    }

    void toSession() {
        if (ambianceWasVisible) host.showAmbiance();
        ambianceWasVisible = false;
        runPending();
    }

    void whenSession(AppMode current, Runnable action) {
        pendingForSession = action;
        if (current == AppMode.SESSION) runPending();
    }

    private void runPending() {
        Runnable action = pendingForSession;
        pendingForSession = null;
        if (action != null) action.run();
    }
}
