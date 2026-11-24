package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.NameNormalizer;

final class LibraryFilter {

    static final LibraryFilter NONE = new LibraryFilter("");

    private final String query;

    LibraryFilter(String query) {
        this.query = NameNormalizer.normalize(query.strip());
    }

    boolean isEmpty() {
        return query.isEmpty();
    }

    boolean matches(String name) {
        return query.isEmpty() || NameNormalizer.normalize(name).contains(query);
    }
}
