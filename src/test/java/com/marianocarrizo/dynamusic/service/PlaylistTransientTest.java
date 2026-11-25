package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistTransientTest {

    private static final File A = new File("a.flac").getAbsoluteFile();
    private static final File B = new File("b.flac").getAbsoluteFile();
    private static final File C = new File("c.flac").getAbsoluteFile();
    private static final File D = new File("d.flac").getAbsoluteFile();

    private final PlaylistManager manager = new PlaylistManager();

    @Test
    void transientContextIsNotAddedToTheSavedPlaylists() {
        manager.playTransient("Kind of Blue", List.of(A, B), 0);

        assertTrue(manager.getPlaylists().isEmpty());
        assertTrue(manager.isTransientActive());
        assertEquals("Kind of Blue", manager.getActivePlaylistName());
    }

    @Test
    void startsAtTheRequestedTrack() {
        manager.playTransient("Album", List.of(A, B, C), 1);

        assertEquals(Optional.of(B), manager.startCurrent());
        assertEquals(Optional.of(C), manager.next());
    }

    @Test
    void outOfRangeStartIsClamped() {
        manager.playTransient("Album", List.of(A, B), 9);

        assertEquals(Optional.of(B), manager.startCurrent());
    }

    @Test
    void shuffleKeepsTheStartTrackFirst() {
        manager.setShuffle(true);

        manager.playTransient("Album", List.of(A, B, C), 2);

        assertEquals(Optional.of(C), manager.startCurrent());
    }

    @Test
    void emptyTrackListLeavesNothingToPlay() {
        manager.playTransient("Album", List.of(), 0);

        assertEquals(Optional.empty(), manager.startCurrent());
    }

    @Test
    void selectingASavedPlaylistEndsTheTransientContext() {
        manager.createPlaylist("mine");
        manager.playTransient("Album", List.of(A), 0);

        manager.setActivePlaylist("mine");

        assertFalse(manager.isTransientActive());
        assertEquals("mine", manager.getActivePlaylistName());
    }

    @Test
    void editingASavedPlaylistWithTheSameNameDoesNotTouchTheTransientOrder() {
        manager.createPlaylist("Album");
        manager.addTrack("Album", C);
        manager.playTransient("Album", List.of(A, B), 1);

        manager.addTrack("Album", D);
        manager.removeTrack("Album", 0);

        assertEquals(Optional.of(B), manager.startCurrent());
        assertEquals(2, manager.size());
        assertTrue(manager.isTransientActive());
    }

    @Test
    void deletingASavedPlaylistWithTheSameNameKeepsTheTransientContext() {
        manager.createPlaylist("Album");
        manager.playTransient("Album", List.of(A, B), 0);

        manager.deletePlaylist("Album");

        assertEquals(Optional.of(A), manager.startCurrent());
        assertTrue(manager.isTransientActive());
    }

    @Test
    void removingFromTheActiveContextLeavesAnEqualNamedSavedPlaylistAlone() {
        manager.createPlaylist("Album");
        manager.addTrack("Album", C);
        manager.playTransient("Album", List.of(A, B), 0);

        manager.removeActive(0);

        assertEquals(1, manager.size());
        assertEquals(Optional.of(B), manager.startCurrent());
        assertEquals(List.of(C.getAbsolutePath()), manager.findByName("Album").orElseThrow().getPaths());
    }

    @Test
    void reorderingTheActiveContextLeavesAnEqualNamedSavedPlaylistAlone() {
        manager.createPlaylist("album");
        manager.addTrack("album", C);
        manager.addTrack("album", D);
        manager.playTransient("Album", List.of(A, B), 0);

        manager.reorderActive(0, 1);

        assertEquals(List.of(B.getAbsolutePath(), A.getAbsolutePath()), manager.getActivePaths());
        assertEquals(List.of(C.getAbsolutePath(), D.getAbsolutePath()), manager.findByName("album").orElseThrow().getPaths());
    }

    @Test
    void activeEditsWorkOnASavedActivePlaylist() {
        manager.createPlaylist("mine");
        manager.addTrack("mine", A);
        manager.addTrack("mine", B);
        manager.setActivePlaylist("mine");

        manager.reorderActive(0, 1);
        manager.removeActive(0);

        assertEquals(List.of(A.getAbsolutePath()), manager.findByName("mine").orElseThrow().getPaths());
    }

    @Test
    void activeEditsWithoutAnActivePlaylistDoNothing() {
        manager.removeActive(0);
        manager.reorderActive(0, 1);

        assertEquals(0, manager.size());
    }
}
