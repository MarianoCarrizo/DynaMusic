package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.LoadState;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.io.File;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class PendingLoads {

    static final long FAILURE_DISPLAY_MILLIS = 3000;
    static final long LOADING_TIMEOUT_MILLIS = 60000;
    static final int NO_TICKET = -1;

    private record Request(int ticket, NowPlayingSource source, TrackType moodType, File file, String title,
                           LoadState state, String message, long changedAt) {
    }

    private final LongSupplier clock;
    private int latest;
    private Request request;

    PendingLoads(LongSupplier clock) {
        this.clock = clock;
    }

    int begin(NowPlayingSource source, TrackType moodType, File file, String title) {
        latest++;
        request = new Request(latest, source, moodType, file, title, LoadState.LOADING, "", clock.getAsLong());
        return latest;
    }

    boolean finish(int ticket) {
        if (request == null || request.ticket() != ticket || request.state() != LoadState.LOADING) return false;
        request = null;
        return true;
    }

    boolean fail(int ticket, String message) {
        if (request == null || request.ticket() != ticket || request.state() != LoadState.LOADING) return false;
        request = new Request(ticket, request.source(), request.moodType(), request.file(), request.title(),
                LoadState.FAILED, message, clock.getAsLong());
        return true;
    }

    boolean isActive() {
        expireStale();
        return request != null;
    }

    Optional<PendingLoad> current(Function<File, Optional<TrackTags>> tags) {
        expireStale();
        if (request == null) return Optional.empty();
        Optional<TrackTags> known = tags.apply(request.file());
        SoundingTrack requested = request.title() == null || request.title().isBlank()
                ? SoundingTrack.of(request.file(), known, false)
                : SoundingTrack.of(request.title(), request.file(), known, false);
        return Optional.of(new PendingLoad(request.source(), request.moodType(),
                requested.withState(request.state(), request.message())));
    }

    private void expireStale() {
        if (request == null) return;
        long limit = request.state() == LoadState.FAILED ? FAILURE_DISPLAY_MILLIS : LOADING_TIMEOUT_MILLIS;
        if (clock.getAsLong() - request.changedAt() >= limit) request = null;
    }
}
