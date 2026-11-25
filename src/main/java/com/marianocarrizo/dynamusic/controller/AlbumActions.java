package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.library.Album;

interface AlbumActions {

    void open(Album album);

    void play(Album album, int start);
}
