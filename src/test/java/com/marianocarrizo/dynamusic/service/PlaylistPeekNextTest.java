package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.model.RepeatMode;
import java.io.File;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaylistPeekNextTest {

    private static final String PLAYLIST = "mine";

    private PlaylistManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlaylistManager();
        manager.createPlaylist(PLAYLIST);
        manager.setActivePlaylist(PLAYLIST);
        for (String track : new String[] {"a.mp3", "b.mp3", "c.mp3"}) manager.addTrack(PLAYLIST, new File(track));
    }

    private static String nameOf(Optional<File> file) {
        return file.orElseThrow().getName();
    }

    @Test
    void middleTrackPeeksTheFollowingOne() {
        manager.startAt(0);

        assertEquals("b.mp3", nameOf(manager.peekNext()));
    }

    @Test
    void lastTrackWithRepeatOffPeeksNothing() {
        manager.startAt(2);

        assertTrue(manager.peekNext().isEmpty());
    }

    @Test
    void repeatOnePeeksTheCurrentTrack() {
        manager.setRepeat(RepeatMode.ONE);
        manager.startAt(1);

        assertEquals("b.mp3", nameOf(manager.peekNext()));
    }

    @Test
    void repeatAllWrapsToTheFirstTrackWithoutShuffle() {
        manager.setRepeat(RepeatMode.ALL);
        manager.startAt(2);

        assertEquals("a.mp3", nameOf(manager.peekNext()));
    }

    @Test
    void repeatAllWithShuffleAtTheEndPeeksNothingBecauseTheNextOrderIsUnknown() {
        manager.setRepeat(RepeatMode.ALL);
        manager.setShuffle(true);
        manager.startCurrent();
        while (manager.peekNext().isPresent()) manager.next();

        assertTrue(manager.peekNext().isEmpty());
    }

    @Test
    void shuffleMidOrderPeeksTheTrackNextWillPlay() {
        manager.setShuffle(true);
        manager.startCurrent();

        File peeked = manager.peekNext().orElseThrow();

        assertEquals(peeked, manager.next().orElseThrow());
    }

    @Test
    void peekingRepeatedlyDoesNotMoveTheCursor() {
        manager.startAt(0);

        File first = manager.peekNext().orElseThrow();
        manager.peekNext();
        manager.peekNext();

        assertEquals(0, manager.currentDisplayIndex());
        assertEquals(first, manager.next().orElseThrow());
    }

    @Test
    void singleTrackFollowsTheRepeatRules() {
        PlaylistManager single = new PlaylistManager();
        single.createPlaylist(PLAYLIST);
        single.setActivePlaylist(PLAYLIST);
        single.addTrack(PLAYLIST, new File("only.mp3"));
        single.startAt(0);

        assertTrue(single.peekNext().isEmpty());
        single.setRepeat(RepeatMode.ALL);
        assertEquals("only.mp3", nameOf(single.peekNext()));
        single.setRepeat(RepeatMode.ONE);
        assertEquals("only.mp3", nameOf(single.peekNext()));
    }

    @Test
    void noActivePlaylistPeeksNothing() {
        assertTrue(new PlaylistManager().peekNext().isEmpty());
    }
}
