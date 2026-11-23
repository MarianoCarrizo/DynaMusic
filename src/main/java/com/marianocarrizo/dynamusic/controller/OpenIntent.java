package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.AudioFileCollector;

import java.io.File;
import java.util.List;
import java.util.Optional;

record OpenIntent(Optional<File> folder, Optional<File> track) {

    static OpenIntent of(List<File> items) {
        Optional<File> folder = items.stream().filter(File::isDirectory).findFirst();
        Optional<File> track = items.stream()
                .filter(item -> item.isFile() && AudioFileCollector.isAudioFile(item))
                .findFirst();
        return new OpenIntent(folder, track);
    }

    boolean isEmpty() {
        return folder.isEmpty() && track.isEmpty();
    }
}
