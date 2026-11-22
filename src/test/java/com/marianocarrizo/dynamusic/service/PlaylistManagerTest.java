package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistManagerTest {

    private static final String PLAYLIST = "mine";

    private PlaylistManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlaylistManager();
        manager.createPlaylist(PLAYLIST);
        manager.setActivePlaylist(PLAYLIST);
        for (String track : new String[] {"a.mp3", "b.mp3", "c.mp3"}) {
            manager.addTrack(PLAYLIST, new File(track));
        }
    }

    @Test
    void selectingTheAlreadyActivePlaylistKeepsTheCurrentPosition() {
        manager.startAt(1);

        manager.setActivePlaylist(PLAYLIST);

        assertEquals(1, manager.currentDisplayIndex());
    }

    @Test
    void playsTheNextTrackInOrderEvenIfTheActivePlaylistIsReselectedBetweenTracks() {
        manager.startAt(0);

        Optional<File> second = manager.next();
        manager.setActivePlaylist(PLAYLIST);
        Optional<File> third = manager.next();

        assertEquals("b.mp3", second.orElseThrow().getName());
        assertEquals("c.mp3", third.orElseThrow().getName());
    }

    @Test
    void stopsAtTheEndOfThePlaylistWhenLoopIsOff() {
        manager.startAt(2);

        assertTrue(manager.next().isEmpty());
    }

    @Test
    void wrapsToTheFirstTrackAtTheEndWhenLoopIsOn() {
        manager.setLoop(true);
        manager.startAt(2);

        assertEquals("a.mp3", manager.next().orElseThrow().getName());
    }
}
