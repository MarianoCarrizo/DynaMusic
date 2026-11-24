package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.RepeatMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackOrderTest {

    private static final String PLAYLIST = "mine";
    private static final File P1 = new File("p1.flac").getAbsoluteFile();
    private static final File P2 = new File("p2.flac").getAbsoluteFile();
    private static final File Q1 = new File("q1.flac").getAbsoluteFile();
    private static final File Q2 = new File("q2.flac").getAbsoluteFile();

    private final PlaylistManager manager = new PlaylistManager();
    private final PlayQueue queue = new PlayQueue();
    private final PlaybackOrder order = new PlaybackOrder(queue, manager);

    @BeforeEach
    void setUp() {
        manager.createPlaylist(PLAYLIST);
        manager.setActivePlaylist(PLAYLIST);
        manager.addTrack(PLAYLIST, P1);
        manager.addTrack(PLAYLIST, P2);
    }

    private void playingFirstTrack() {
        order.playing(manager.startAt(0).orElseThrow());
    }

    @Test
    void queuedTracksPlayBeforeTheRestOfThePlaylist() {
        playingFirstTrack();
        queue.add(List.of(Q1, Q2));

        assertEquals(Optional.of(Q1), order.afterEnd());
        assertEquals(Optional.of(Q2), order.afterEnd());
        assertEquals(Optional.of(P2), order.afterEnd());
    }

    @Test
    void consumedTracksLeaveTheQueue() {
        playingFirstTrack();
        queue.add(List.of(Q1, Q2));

        order.next();

        assertEquals(List.of(Q2), queue.items());
    }

    @Test
    void shuffleNeverReordersTheQueue() {
        manager.setShuffle(true);
        playingFirstTrack();
        queue.add(List.of(Q1));

        assertEquals(Optional.of(Q1), order.next());
    }

    @Test
    void repeatAllResumesAndWrapsThePlaylistOnceTheQueueEmpties() {
        manager.setRepeat(RepeatMode.ALL);
        order.playing(manager.startAt(1).orElseThrow());
        queue.add(List.of(Q1));

        assertEquals(Optional.of(Q1), order.afterEnd());
        assertEquals(Optional.of(P1), order.afterEnd());
    }

    @Test
    void endOfPlaylistWithAnEmptyQueueStops() {
        order.playing(manager.startAt(1).orElseThrow());

        assertEquals(Optional.empty(), order.afterEnd());
    }

    @Test
    void repeatOneReplaysTheSoundingQueuedTrack() {
        manager.setRepeat(RepeatMode.ONE);
        playingFirstTrack();
        queue.add(List.of(Q1, Q2));
        order.next();

        assertEquals(Optional.of(Q1), order.afterEnd());
        assertEquals(List.of(Q2), queue.items());
    }

    @Test
    void repeatOneReplaysTheSoundingPlaylistTrackAndKeepsTheQueue() {
        manager.setRepeat(RepeatMode.ONE);
        playingFirstTrack();
        queue.add(List.of(Q1));

        assertEquals(Optional.of(P1), order.afterEnd());
        assertEquals(List.of(Q1), queue.items());
    }

    @Test
    void manualNextStillConsumesTheQueueUnderRepeatOne() {
        manager.setRepeat(RepeatMode.ONE);
        playingFirstTrack();
        queue.add(List.of(Q1));

        assertEquals(Optional.of(Q1), order.next());
    }

    @Test
    void previousGoesBackInThePlaylist() {
        order.playing(manager.startAt(1).orElseThrow());

        assertEquals(Optional.of(P1), order.previous());
    }

    @Test
    void previousAtTheFirstTrackReplaysItAndKeepsTheQueue() {
        playingFirstTrack();
        queue.add(List.of(Q1));

        assertEquals(Optional.of(P1), order.previous());
        assertEquals(List.of(Q1), queue.items());
    }

    @Test
    void failedQueuedTrackIsDroppedAndTheNextOneIsTried() {
        playingFirstTrack();
        queue.add(List.of(Q1, Q2));

        assertEquals(Optional.of(Q1), order.next());
        assertEquals(Optional.of(Q2), order.next());
        assertEquals(Optional.of(P2), order.next());
    }

    @Test
    void peekNextShowsTheQueueHeadWithoutConsuming() {
        playingFirstTrack();
        queue.add(List.of(Q1));

        assertEquals(Optional.of(Q1), order.peekNext());
        assertEquals(List.of(Q1), queue.items());
    }

    @Test
    void peekNextFallsBackToThePlaylist() {
        playingFirstTrack();

        assertEquals(Optional.of(P2), order.peekNext());
    }

    @Test
    void peekNextUnderRepeatOneIsTheSoundingTrack() {
        manager.setRepeat(RepeatMode.ONE);
        playingFirstTrack();
        queue.add(List.of(Q1));

        assertEquals(Optional.of(P1), order.peekNext());
    }

    @Test
    void startCurrentPrefersThePlaylistTrack() {
        queue.add(List.of(Q1));

        assertEquals(Optional.of(P1), order.startCurrent());
        assertEquals(List.of(Q1), queue.items());
    }

    @Test
    void startCurrentFallsBackToTheQueueWithoutAPlaylist() {
        PlayQueue lone = new PlayQueue();
        lone.add(List.of(Q1));

        assertEquals(Optional.of(Q1), new PlaybackOrder(lone, new PlaylistManager()).startCurrent());
        assertTrue(lone.isEmpty());
    }

    @Test
    void afterAStandaloneTrackOnlyTheQueueContinues() {
        queue.add(List.of(Q1));

        assertEquals(Optional.of(Q1), order.afterStandalone());
        assertEquals(Optional.empty(), order.afterStandalone());
    }

    @Test
    void previousWhileAQueuedTrackSoundsReturnsToTheCurrentPlaylistTrack() {
        order.playing(manager.startAt(1).orElseThrow());
        queue.add(List.of(Q1));
        assertEquals(Optional.of(Q1), order.next());

        assertEquals(Optional.of(P2), order.previous());
        assertEquals(Optional.of(P1), order.previous());
    }

    @Test
    void aDisabledQueueIsNeverConsumedOrPeeked() {
        PlaybackOrder session = new PlaybackOrder(queue, manager, () -> false);
        session.playing(manager.startAt(0).orElseThrow());
        queue.add(List.of(Q1));

        assertEquals(Optional.of(P2), session.peekNext());
        assertEquals(Optional.of(P2), session.afterEnd());
        assertEquals(Optional.empty(), session.afterStandalone());
        assertEquals(List.of(Q1), queue.items());
    }

    @Test
    void aDisabledQueueDoesNotStartPlaybackAfterAStandaloneTrack() {
        PlayQueue lone = new PlayQueue();
        lone.add(List.of(Q1));
        PlaybackOrder session = new PlaybackOrder(lone, new PlaylistManager(), () -> false);

        assertEquals(Optional.empty(), session.startCurrent());
        assertEquals(Optional.empty(), session.afterStandalone());
        assertEquals(List.of(Q1), lone.items());
    }
}
