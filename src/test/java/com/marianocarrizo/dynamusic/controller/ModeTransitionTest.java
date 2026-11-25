package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeTransitionTest {

    private static final class FakeHost implements ModeTransition.Host {
        boolean moodPlaying;
        boolean ambianceVisible;
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean moodPlaying() {
            return moodPlaying;
        }

        @Override
        public void pauseMood() {
            calls.add("pauseMood");
            moodPlaying = false;
        }

        @Override
        public boolean ambianceVisible() {
            return ambianceVisible;
        }

        @Override
        public void hideAmbiance() {
            calls.add("hideAmbiance");
            ambianceVisible = false;
        }

        @Override
        public void showAmbiance() {
            calls.add("showAmbiance");
            ambianceVisible = true;
        }
    }

    private final FakeHost host = new FakeHost();
    private final ModeTransition transition = new ModeTransition(host);

    @Test
    void listenPausesAPlayingMood() {
        host.moodPlaying = true;

        transition.toListen();

        assertEquals(List.of("pauseMood"), host.calls);
    }

    @Test
    void listenDoesNotTouchAMoodThatIsNotPlaying() {
        transition.toListen();

        assertTrue(host.calls.isEmpty());
    }

    @Test
    void returningToSessionNeverResumesTheMood() {
        host.moodPlaying = true;
        transition.toListen();
        host.calls.clear();

        transition.toSession();

        assertTrue(host.calls.isEmpty());
    }

    @Test
    void anOpenAmbianceIsHiddenForListenAndRestoredForSession() {
        host.ambianceVisible = true;

        transition.toListen();
        transition.toSession();

        assertEquals(List.of("hideAmbiance", "showAmbiance"), host.calls);
    }

    @Test
    void aClosedAmbianceStaysClosedOnARoundTrip() {
        transition.toListen();
        transition.toSession();

        assertTrue(host.calls.isEmpty());
    }

    @Test
    void deferredActionRunsAtOnceInSession() {
        List<String> ran = new ArrayList<>();

        transition.whenSession(AppMode.SESSION, () -> ran.add("open"));

        assertEquals(List.of("open"), ran);
    }

    @Test
    void deferredActionWaitsForTheFirstSessionEntry() {
        List<String> ran = new ArrayList<>();

        transition.whenSession(AppMode.LISTEN, () -> ran.add("open"));
        assertTrue(ran.isEmpty());

        transition.toSession();
        transition.toListen();
        transition.toSession();

        assertEquals(List.of("open"), ran);
    }
}
