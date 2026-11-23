package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackNamesTest {

    @Test
    void titleDropsTheExtensionAndKeepsTheTrackNumber() {
        assertEquals("04 Opening Stage X", TrackNames.title("04 Opening Stage X.flac"));
    }

    @Test
    void titleOnlyDropsTheLastExtension() {
        assertEquals("Song v1.2", TrackNames.title("Song v1.2.mp3"));
    }

    @Test
    void titleWithoutExtensionIsUnchanged() {
        assertEquals("Song", TrackNames.title("Song"));
    }

    @Test
    void leadingDotNamesAreNotTreatedAsExtensions() {
        assertEquals(".hidden", TrackNames.title(".hidden"));
    }

    @Test
    void albumIsTheParentFolderName() {
        assertEquals("Mega Man X", TrackNames.album(new File("music/Mega Man X/04 Opening Stage X.flac")));
    }

    @Test
    void albumOfAFileInTheManagedLibraryIsEmpty() {
        File pinned = TrackLibrary.libraryDir().resolve("song.mp3").toFile();

        assertEquals("", TrackNames.album(pinned));
    }

    @Test
    void albumOfAFileInTheDownloadCacheIsEmpty() {
        File cached = YouTubeDownloader.cacheDirPath().resolve("song.mp3").toFile();

        assertEquals("", TrackNames.album(cached));
    }
}
