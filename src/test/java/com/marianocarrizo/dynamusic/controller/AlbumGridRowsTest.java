package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.library.AlbumKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlbumGridRowsTest {

    private static List<Album> albums(int count) {
        List<Album> albums = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            albums.add(new Album(new AlbumKey("album " + i, "/m/" + i), "Album " + i, "Ana", "", List.of(), ""));
        }
        return albums;
    }

    @Test
    void splitsIntoFullRowsAndAShorterLastRow() {
        List<List<Album>> rows = AlbumGridRows.of(albums(7), 3);

        assertEquals(List.of(3, 3, 1), rows.stream().map(List::size).toList());
    }

    @Test
    void keepsTheOriginalOrder() {
        List<Album> albums = albums(5);

        List<List<Album>> rows = AlbumGridRows.of(albums, 2);

        assertEquals(albums, rows.stream().flatMap(List::stream).toList());
    }

    @Test
    void exactMultipleLeavesNoEmptyRow() {
        assertEquals(2, AlbumGridRows.of(albums(6), 3).size());
    }

    @Test
    void noAlbumsGiveNoRows() {
        assertTrue(AlbumGridRows.of(List.of(), 4).isEmpty());
    }

    @Test
    void columnCountBelowOneIsTreatedAsOne() {
        assertEquals(3, AlbumGridRows.of(albums(3), 0).size());
        assertEquals(3, AlbumGridRows.of(albums(3), -2).size());
    }

    @Test
    void columnsFitTheWidthIncludingGaps() {
        assertEquals(3, AlbumGridRows.columnsFor(500, 150, 10));
        assertEquals(3, AlbumGridRows.columnsFor(470, 150, 10));
        assertEquals(2, AlbumGridRows.columnsFor(469, 150, 10));
        assertEquals(4, AlbumGridRows.columnsFor(630, 150, 10));
    }

    @Test
    void narrowOrUnknownWidthStillGivesOneColumn() {
        assertEquals(1, AlbumGridRows.columnsFor(40, 150, 10));
        assertEquals(1, AlbumGridRows.columnsFor(0, 150, 10));
    }
}
