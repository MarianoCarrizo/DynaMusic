package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;

import java.util.ArrayList;
import java.util.List;

final class AlbumGridRows {

    private AlbumGridRows() {
    }

    static List<List<Album>> of(List<Album> albums, int columns) {
        int perRow = Math.max(columns, 1);
        List<List<Album>> rows = new ArrayList<>();
        for (int start = 0; start < albums.size(); start += perRow) {
            rows.add(List.copyOf(albums.subList(start, Math.min(start + perRow, albums.size()))));
        }
        return rows;
    }

    static int columnsFor(double width, double tileWidth, double gap) {
        return Math.max(1, (int) Math.floor((width + gap) / (tileWidth + gap)));
    }
}
