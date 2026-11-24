package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.RepeatMode;

import java.io.File;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class PlaybackOrder {

    private final PlayQueue queue;
    private final PlaylistManager context;
    private final BooleanSupplier queueEnabled;
    private File sounding;
    private boolean soundingFromQueue;

    public PlaybackOrder(PlayQueue queue, PlaylistManager context) {
        this(queue, context, () -> true);
    }

    public PlaybackOrder(PlayQueue queue, PlaylistManager context, BooleanSupplier queueEnabled) {
        this.queue = queue;
        this.context = context;
        this.queueEnabled = queueEnabled;
    }

    public void playing(File file) {
        sounding = file;
        soundingFromQueue = false;
    }

    public Optional<File> next() {
        Optional<File> queued = pollQueue();
        if (queued.isPresent()) return remember(queued, true);
        return remember(context.next(), false);
    }

    public Optional<File> afterEnd() {
        if (repeatsSounding()) return soundingFromQueue ? Optional.of(sounding) : context.nextAfterEnd();
        return next();
    }

    public Optional<File> afterStandalone() {
        return remember(pollQueue(), true);
    }

    public Optional<File> previous() {
        if (soundingFromQueue) return remember(context.startCurrent(), false);
        return remember(context.prev(), false);
    }

    public Optional<File> startCurrent() {
        Optional<File> current = context.startCurrent();
        if (current.isPresent()) return remember(current, false);
        return remember(pollQueue(), true);
    }

    public Optional<File> peekNext() {
        if (repeatsSounding() && soundingFromQueue) return Optional.of(sounding);
        if (repeatsSounding()) return context.peekNext();
        Optional<File> queued = queueEnabled.getAsBoolean() ? queue.peek() : Optional.empty();
        return queued.isPresent() ? queued : context.peekNext();
    }

    private Optional<File> pollQueue() {
        return queueEnabled.getAsBoolean() ? queue.poll() : Optional.empty();
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
