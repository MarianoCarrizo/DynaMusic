package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;
import com.marianocarrizo.dynamusic.library.Artist;
import com.marianocarrizo.dynamusic.library.LibrarySnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

final class BrowseNavigator {

    enum Page {
        ALBUMS,
        ARTISTS,
        ARTIST_ALBUMS,
        ALBUM_DETAIL
    }

    private record View(Page page, Album album, Artist artist) {
    }

    private final Deque<View> stack = new ArrayDeque<>();
    private BrowseSection section = BrowseSection.ALBUMS;

    BrowseNavigator() {
        showSection(BrowseSection.ALBUMS);
    }

    Page page() {
        return stack.peek().page();
    }

    Album album() {
        return stack.peek().album();
    }

    Artist artist() {
        return stack.peek().artist();
    }

    BrowseSection section() {
        return section;
    }

    boolean canGoBack() {
        return stack.size() > 1;
    }

    void showSection(BrowseSection target) {
        section = target;
        stack.clear();
        stack.push(new View(target == BrowseSection.ALBUMS ? Page.ALBUMS : Page.ARTISTS, null, null));
    }

    void openAlbum(Album album) {
        stack.push(new View(Page.ALBUM_DETAIL, album, null));
    }

    void openArtist(Artist artist) {
        stack.push(new View(Page.ARTIST_ALBUMS, null, artist));
    }

    void back() {
        if (canGoBack()) stack.pop();
    }

    void refresh(LibrarySnapshot snapshot) {
        List<View> bottomUp = new ArrayList<>(stack);
        Collections.reverse(bottomUp);
        stack.clear();
        for (View view : bottomUp) {
            Optional<View> current = reconcile(view, snapshot);
            if (current.isEmpty()) return;
            stack.push(current.get());
        }
    }

    private static Optional<View> reconcile(View view, LibrarySnapshot snapshot) {
        if (view.album() != null) {
            return snapshot.albums().stream().filter(album -> album.key().equals(view.album().key())).findFirst()
                    .map(album -> new View(view.page(), album, view.artist()));
        }
        if (view.artist() != null) {
            return snapshot.artists().stream().filter(artist -> artist.name().equals(view.artist().name())).findFirst()
                    .map(artist -> new View(view.page(), null, artist));
        }
        return Optional.of(view);
    }
}
