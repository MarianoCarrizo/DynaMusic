package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.RepeatMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        manager.setRepeat(RepeatMode.ALL);
        manager.startAt(2);

        assertEquals("a.mp3", manager.next().orElseThrow().getName());
    }

    @Test
    void insertsAtTheStartMiddleAndEnd() {
        manager.insertTracks(PLAYLIST, files("x.mp3"), 0);
        manager.insertTracks(PLAYLIST, files("y.mp3"), 2);
        manager.insertTracks(PLAYLIST, files("z.mp3"), 99);

        assertEquals(List.of("x.mp3", "a.mp3", "y.mp3", "b.mp3", "c.mp3", "z.mp3"), names());
    }

    @Test
    void skipsTracksAlreadyInThePlaylistAndRepeatsInsideTheBatch() {
        int added = manager.insertTracks(PLAYLIST, files("b.mp3", "n.mp3", "n.mp3"), 0);

        assertEquals(1, added);
        assertEquals(List.of("n.mp3", "a.mp3", "b.mp3", "c.mp3"), names());
    }

    @Test
    void insertingBeforeThePlayingTrackKeepsItPlaying() {
        manager.startAt(1);

        manager.insertTracks(PLAYLIST, files("x.mp3", "y.mp3"), 0);

        assertEquals("b.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(3, manager.currentDisplayIndex());
        assertEquals("c.mp3", manager.next().orElseThrow().getName());
    }

    @Test
    void movingATrackFromBeforeToAfterThePlayingTrackKeepsItPlaying() {
        manager.startAt(1);

        manager.reorderTrack(PLAYLIST, 0, 2);

        assertEquals(List.of("b.mp3", "c.mp3", "a.mp3"), names());
        assertEquals("b.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(0, manager.currentDisplayIndex());
    }

    @Test
    void movingATrackFromAfterToBeforeThePlayingTrackKeepsItPlaying() {
        manager.startAt(1);

        manager.reorderTrack(PLAYLIST, 2, 0);

        assertEquals(List.of("c.mp3", "a.mp3", "b.mp3"), names());
        assertEquals("b.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(2, manager.currentDisplayIndex());
    }

    @Test
    void movingThePlayingTrackItselfMakesThePositionFollowIt() {
        manager.startAt(0);

        manager.reorderTrack(PLAYLIST, 0, 2);

        assertEquals("a.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(2, manager.currentDisplayIndex());
        assertTrue(manager.next().isEmpty());
    }

    @Test
    void reorderingWithShuffleOnKeepsThePlayingTrackAndTheShuffledSequence() {
        manager.insertTracks(PLAYLIST, files("d.mp3", "e.mp3", "f.mp3"), 3);
        manager.setShuffle(true);
        manager.startAt(2);
        List<String> before = fullSequence();
        manager.startAt(2);

        manager.reorderTrack(PLAYLIST, 5, 0);

        assertEquals("c.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(before, fullSequence());
    }

    @Test
    void insertingWithShuffleOnKeepsThePlayingTrackAndPlaysTheNewTracksLast() {
        manager.setShuffle(true);
        manager.startAt(1);
        List<String> before = fullSequence();
        manager.startAt(1);

        manager.insertTracks(PLAYLIST, files("x.mp3"), 0);

        List<String> expected = new ArrayList<>(before);
        expected.add("x.mp3");
        assertEquals("b.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(expected, fullSequence());
    }

    @Test
    void removingATrackBeforeThePlayingOneKeepsItPlaying() {
        manager.startAt(2);

        manager.removeTrack(PLAYLIST, 0);

        assertEquals("c.mp3", manager.getCurrentTrack().orElseThrow().getName());
        assertEquals(1, manager.currentDisplayIndex());
    }

    @Test
    void afterRemovingThePlayingTrackNextPlaysItsSuccessorWithoutSkipping() {
        manager.startAt(0);

        manager.removeTrack(PLAYLIST, 0);

        assertEquals(-1, manager.currentDisplayIndex());
        assertEquals("b.mp3", manager.next().orElseThrow().getName());
        assertEquals("c.mp3", manager.next().orElseThrow().getName());
    }

    @Test
    void afterRemovingThePlayingTrackWithShuffleOnNextPlaysTheSuccessorInTheSequence() {
        manager.setShuffle(true);
        manager.startAt(1);
        List<String> sequence = fullSequence();
        manager.startAt(1);
        int playingAt = sequence.indexOf("b.mp3");
        manager.removeTrack(PLAYLIST, 1);

        Optional<File> next = manager.next();

        if (playingAt == sequence.size() - 1) {
            assertTrue(next.isEmpty());
        } else {
            assertEquals(sequence.get(playingAt + 1), next.orElseThrow().getName());
        }
    }

    @Test
    void pressingPlayAfterRemovingThePlayingTrackStartsTheSuccessor() {
        manager.startAt(1);

        manager.removeTrack(PLAYLIST, 1);

        assertEquals("c.mp3", manager.startCurrent().orElseThrow().getName());
        assertEquals(1, manager.currentDisplayIndex());
        assertEquals("c.mp3", manager.getCurrentTrack().orElseThrow().getName());
    }

    @Test
    void afterRemovingThePlayingLastTrackNextStopsWhenLoopIsOff() {
        manager.startAt(2);

        manager.removeTrack(PLAYLIST, 2);

        assertTrue(manager.next().isEmpty());
    }

    @Test
    void afterRemovingThePlayingLastTrackNextWrapsToTheFirstWhenLoopIsOn() {
        manager.setRepeat(RepeatMode.ALL);
        manager.startAt(2);

        manager.removeTrack(PLAYLIST, 2);

        assertEquals("a.mp3", manager.next().orElseThrow().getName());
    }

    @Test
    void removingTheOnlyPlayingTrackLeavesNothingToPlay() {
        manager.removeTrack(PLAYLIST, 2);
        manager.removeTrack(PLAYLIST, 1);
        manager.startAt(0);

        manager.removeTrack(PLAYLIST, 0);

        assertTrue(manager.next().isEmpty());
        assertTrue(manager.getCurrentTrack().isEmpty());
        assertEquals(0, manager.size());
    }

    @Test
    void skipsDuplicatesThatDifferOnlyByCaseOnCaseInsensitiveSystems() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));

        int added = manager.insertTracks(PLAYLIST, files("A.MP3"), 0);

        assertEquals(0, added);
    }

    private List<String> fullSequence() {
        for (int i = 0; i < manager.size(); i++) manager.prev();
        List<String> played = new ArrayList<>();
        played.add(manager.getCurrentTrack().orElseThrow().getName());
        Optional<File> next = manager.next();
        while (next.isPresent()) {
            played.add(next.get().getName());
            next = manager.next();
        }
        return played;
    }

    private List<String> names() {
        return manager.getActivePaths().stream().map(path -> new File(path).getName()).toList();
    }

    private static List<File> files(String... names) {
        return Arrays.stream(names).map(File::new).toList();
    }

    @Test
    void endOfTrackStopsAtTheEndOfThePlaylistWhenRepeatIsOff() {
        manager.startAt(2);

        assertTrue(manager.nextAfterEnd().isEmpty());
    }

    @Test
    void endOfTrackAdvancesWhenRepeatIsOff() {
        manager.startAt(0);

        assertEquals("b.mp3", manager.nextAfterEnd().orElseThrow().getName());
    }

    @Test
    void endOfTrackWrapsToTheFirstTrackWhenRepeatIsAll() {
        manager.setRepeat(RepeatMode.ALL);
        manager.startAt(2);

        assertEquals("a.mp3", manager.nextAfterEnd().orElseThrow().getName());
    }

    @Test
    void endOfTrackRepeatsTheSameTrackWhenRepeatIsOne() {
        manager.setRepeat(RepeatMode.ONE);
        manager.startAt(1);

        assertEquals("b.mp3", manager.nextAfterEnd().orElseThrow().getName());
        assertEquals("b.mp3", manager.nextAfterEnd().orElseThrow().getName());
        assertEquals(1, manager.currentDisplayIndex());
    }

    @Test
    void endOfTrackRepeatsTheSameTrackWhenRepeatIsOneWithShuffle() {
        manager.setRepeat(RepeatMode.ONE);
        manager.setShuffle(true);
        manager.startAt(2);

        for (int i = 0; i < 5; i++) {
            assertEquals("c.mp3", manager.nextAfterEnd().orElseThrow().getName());
        }
        assertEquals(2, manager.currentDisplayIndex());
    }

    @Test
    void manualNextStillAdvancesWhenRepeatIsOne() {
        manager.setRepeat(RepeatMode.ONE);
        manager.startAt(0);

        assertEquals("b.mp3", manager.next().orElseThrow().getName());
    }

    @Test
    void manualPreviousStillGoesBackWhenRepeatIsOne() {
        manager.setRepeat(RepeatMode.ONE);
        manager.startAt(2);

        assertEquals("b.mp3", manager.prev().orElseThrow().getName());
    }
}
