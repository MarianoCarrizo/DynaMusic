package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.RepeatMode;

import java.io.File;
import java.util.Optional;

public final class PlaybackOrder {

    private final PlayQueue queue;
    private final PlaylistManager context;
    private File sounding;
    private boolean soundingFromQueue;

    public PlaybackOrder(PlayQueue queue, PlaylistManager context) {
        this.queue = queue;
        this.context = context;
    }

    public void playing(File file) {
        sounding = file;
        soundingFromQueue = false;
    }

    public Optional<File> next() {
        Optional<File> queued = queue.poll();
        if (queued.isPresent()) return remember(queued, true);
        return remember(context.next(), false);
    }

    public Optional<File> afterEnd() {
        if (repeatsSounding()) return soundingFromQueue ? Optional.of(sounding) : context.nextAfterEnd();
        return next();
    }

    public Optional<File> afterStandalone() {
        return remember(queue.poll(), true);
    }

    public Optional<File> previous() {
        return remember(context.prev(), false);
    }

    public Optional<File> startCurrent() {
        Optional<File> current = context.startCurrent();
        if (current.isPresent()) return remember(current, false);
        return remember(queue.poll(), true);
    }

    public Optional<File> peekNext() {
        if (repeatsSounding() && soundingFromQueue) return Optional.of(sounding);
        if (repeatsSounding()) return context.peekNext();
        Optional<File> queued = queue.peek();
        return queued.isPresent() ? queued : context.peekNext();
    }

    private boolean repeatsSounding() {
        return context.getRepeat() == RepeatMode.ONE && sounding != null;
    }

    private Optional<File> remember(Optional<File> file, boolean fromQueue) {
        file.ifPresent(f -> {
            sounding = f;
            soundingFromQueue = fromQueue;
        });
        return file;
    }
}
