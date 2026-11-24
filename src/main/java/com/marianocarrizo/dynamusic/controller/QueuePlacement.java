package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.service.PlayQueue;

import java.io.File;
import java.util.List;

enum QueuePlacement {
    NEXT {
        @Override
        void apply(PlayQueue queue, List<File> files) {
            queue.playNext(files);
        }
    },
    END {
        @Override
        void apply(PlayQueue queue, List<File> files) {
            queue.add(files);
        }
    };

    abstract void apply(PlayQueue queue, List<File> files);
}
