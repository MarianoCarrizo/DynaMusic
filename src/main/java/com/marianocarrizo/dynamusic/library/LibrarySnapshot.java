package com.marianocarrizo.dynamusic.library;

import java.util.List;

public record LibrarySnapshot(List<Album> albums, List<Artist> artists, int trackCount) {

    public static final LibrarySnapshot EMPTY = new LibrarySnapshot(List.of(), List.of(), 0);
}
