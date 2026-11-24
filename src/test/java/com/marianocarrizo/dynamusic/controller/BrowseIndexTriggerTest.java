package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowseIndexTriggerTest {

    private static final File FIRST = new File("music-a");
    private static final File SECOND = new File("music-b");

    private final BrowseIndexTrigger trigger = new BrowseIndexTrigger();

    @Test
    void nothingStartsInSession() {
        assertTrue(trigger.due(AppMode.SESSION, FIRST).isEmpty());
    }

    @Test
    void nothingStartsWithoutAFolder() {
        assertTrue(trigger.due(AppMode.LISTEN, null).isEmpty());
    }

    @Test
    void firstListenEntryWithAFolderStartsTheIndex() {
        assertEquals(Optional.of(FIRST), trigger.due(AppMode.LISTEN, FIRST));
    }

    @Test
    void sameFolderIsNotIndexedTwice() {
        trigger.due(AppMode.LISTEN, FIRST);

        assertTrue(trigger.due(AppMode.LISTEN, FIRST).isEmpty());
    }

    @Test
    void goingToSessionAndBackDoesNotRestartIt() {
        trigger.due(AppMode.LISTEN, FIRST);
        trigger.due(AppMode.SESSION, FIRST);

        assertTrue(trigger.due(AppMode.LISTEN, FIRST).isEmpty());
    }

    @Test
    void folderChangeInListenRestartsIt() {
        trigger.due(AppMode.LISTEN, FIRST);

        assertEquals(Optional.of(SECOND), trigger.due(AppMode.LISTEN, SECOND));
    }

    @Test
    void folderChangeInSessionWaitsForTheNextListenEntry() {
        trigger.due(AppMode.LISTEN, FIRST);

        assertTrue(trigger.due(AppMode.SESSION, SECOND).isEmpty());
        assertEquals(Optional.of(SECOND), trigger.due(AppMode.LISTEN, SECOND));
    }
}
