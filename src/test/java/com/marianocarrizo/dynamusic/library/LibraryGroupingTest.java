package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryGroupingTest {

    private static TrackEntry entry(String path, String title, String artist, String album, int track) {
        return new TrackEntry(Path.of(path).toString(), 1, 1, new TrackTags(title, artist, album, track, "", false));
    }

    private static TrackEntry untagged(String path) {
        return new TrackEntry(Path.of(path).toString(), 1, 1, TrackTags.EMPTY);
    }

    private static List<String> titles(Album album) {
        return album.tracks().stream().map(track -> track.tags().title()).toList();
    }

    @Test
    void untaggedFileUsesItsFolderAsAlbumAndUnknownArtist() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(untagged("/m/Rain/a.flac")));

        assertEquals(1, snapshot.albums().size());
        assertEquals("Rain", snapshot.albums().get(0).title());
        assertEquals("Unknown artist", snapshot.albums().get(0).artist());
        assertEquals("Unknown artist", snapshot.artists().get(0).name());
    }

    @Test
    void emptyInputGivesAnEmptySnapshot() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of());

        assertTrue(snapshot.albums().isEmpty());
        assertTrue(snapshot.artists().isEmpty());
        assertEquals(0, snapshot.trackCount());
    }

    @Test
    void tracksOfTheSameAlbumAndFolderShareOneAlbum() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/Blue/02.flac", "Two", "Ana", "Blue", 2),
                entry("/m/Blue/01.flac", "One", "Ana", "Blue", 1)));

        assertEquals(1, snapshot.albums().size());
        assertEquals(List.of("One", "Two"), titles(snapshot.albums().get(0)));
        assertEquals(2, snapshot.trackCount());
    }

    @Test
    void sameAlbumTitleInDifferentFoldersStaysSeparate() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/a/01.flac", "One", "Ana", "Greatest Hits", 1),
                entry("/m/b/01.flac", "One", "Bob", "Greatest Hits", 1)));

        assertEquals(2, snapshot.albums().size());
    }

    @Test
    void albumTitlesThatDifferOnlyByCaseOrAccentsMerge() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/x/01.flac", "One", "Ana", "Café", 1),
                entry("/m/x/02.flac", "Two", "Ana", "cafe", 2)));

        assertEquals(1, snapshot.albums().size());
    }

    @Test
    void discFoldersMergeIntoTheParentAlbumInDiscOrder() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/Big/CD2/01.flac", "Three", "Ana", "Big", 1),
                entry("/m/Big/CD1/02.flac", "Two", "Ana", "Big", 2),
                entry("/m/Big/CD1/01.flac", "One", "Ana", "Big", 1)));

        assertEquals(1, snapshot.albums().size());
        assertEquals(List.of("One", "Two", "Three"), titles(snapshot.albums().get(0)));
        assertEquals(Path.of("/m/Big").toString(), snapshot.albums().get(0).key().folder());
    }

    @Test
    void untaggedDiscFoldersUseTheGrandparentNameAsAlbum() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                untagged("/m/Box Set/Disc 1/a.flac"),
                untagged("/m/Box Set/Disc 2/a.flac")));

        assertEquals(1, snapshot.albums().size());
        assertEquals("Box Set", snapshot.albums().get(0).title());
    }

    @Test
    void albumWithManyArtistsIsVariousArtistsAndEachArtistListsIt() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/Mix/01.flac", "One", "Ana", "Mix", 1),
                entry("/m/Mix/02.flac", "Two", "Bob", "Mix", 2)));

        assertEquals("Various artists", snapshot.albums().get(0).artist());
        assertEquals(List.of("Ana", "Bob"), snapshot.artists().stream().map(Artist::name).toList());
        assertEquals(1, snapshot.artists().get(0).albums().size());
        assertEquals(1, snapshot.artists().get(1).albums().size());
    }

    @Test
    void albumWithOneArtistTakesThatArtist() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/Blue/01.flac", "One", "Ana", "Blue", 1),
                entry("/m/Blue/02.flac", "Two", "ANA", "Blue", 2)));

        assertEquals("Ana", snapshot.albums().get(0).artist());
        assertEquals(1, snapshot.artists().size());
    }

    @Test
    void trackWithoutArtistDoesNotTurnTheAlbumIntoVariousArtists() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/Blue/01.flac", "One", "Ana", "Blue", 1),
                entry("/m/Blue/02.flac", "Two", "", "Blue", 2)));

        assertEquals("Ana", snapshot.albums().get(0).artist());
        assertEquals(List.of("Ana"), snapshot.artists().stream().map(Artist::name).toList());
    }

    @Test
    void artistWithAlbumsInTwoFoldersListsBoth() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/a/01.flac", "One", "Ana", "First", 1),
                entry("/m/b/01.flac", "One", "Ana", "Second", 1)));

        assertEquals(1, snapshot.artists().size());
        assertEquals(List.of("First", "Second"),
                snapshot.artists().get(0).albums().stream().map(Album::title).toList());
    }

    @Test
    void albumsAndArtistsAreSortedIgnoringCaseAndAccents() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/1/01.flac", "t", "zed", "Zebra", 1),
                entry("/m/2/01.flac", "t", "Álvaro", "apple", 1),
                entry("/m/3/01.flac", "t", "Mia", "Mango", 1)));

        assertEquals(List.of("apple", "Mango", "Zebra"), snapshot.albums().stream().map(Album::title).toList());
        assertEquals(List.of("Álvaro", "Mia", "zed"), snapshot.artists().stream().map(Artist::name).toList());
    }

    @Test
    void tracksWithoutNumberFollowNumberedOnesInNameOrder() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(
                entry("/m/Blue/b.flac", "B", "Ana", "Blue", 0),
                entry("/m/Blue/a.flac", "A", "Ana", "Blue", 0),
                entry("/m/Blue/z.flac", "Z", "Ana", "Blue", 1)));

        assertEquals(List.of("Z", "A", "B"), titles(snapshot.albums().get(0)));
    }

    @Test
    void albumTakesTheFirstYearAndTheFirstEmbeddedCover() {
        TrackEntry first = new TrackEntry(Path.of("/m/Blue/01.flac").toString(), 1, 1,
                new TrackTags("One", "Ana", "Blue", 1, "", false));
        TrackEntry second = new TrackEntry(Path.of("/m/Blue/02.flac").toString(), 1, 1,
                new TrackTags("Two", "Ana", "Blue", 2, "1999", true));

        Album album = LibraryGrouping.group(List.of(second, first)).albums().get(0);

        assertEquals("1999", album.year());
        assertEquals(second.path(), album.coverTrack());
    }

    @Test
    void albumWithoutEmbeddedCoverHasNoCoverTrack() {
        Album album = LibraryGrouping.group(List.of(untagged("/m/Rain/a.flac"))).albums().get(0);

        assertEquals("", album.coverTrack());
    }

    @Test
    void fileDirectlyInTheRootFallsBackToAnUnknownAlbumTitle() {
        LibrarySnapshot snapshot = LibraryGrouping.group(List.of(untagged("a.flac")));

        assertEquals("Unknown album", snapshot.albums().get(0).title());
    }
}
