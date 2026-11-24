package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.OutputArbiter;
import java.util.Set;
import java.util.concurrent.Executor;

final class SharedUse {

    private static final Set<PlaybackStatus> SILENT = Set.of(PlaybackStatus.PAUSED, PlaybackStatus.STOPPED);
    private static final Set<PlaybackStatus> TERMINAL = Set.of(PlaybackStatus.HALTED, PlaybackStatus.DISPOSED);

    private final OutputArbiter arbiter;
    private final Object user;
    private final Executor fx;
    private volatile boolean wanted;

    SharedUse(OutputArbiter arbiter, Object user, Executor fx) {
        this.arbiter = arbiter;
        this.user = user;
        this.fx = fx;
    }

    void start(Runnable play) {
        wanted = true;
        arbiter.startShared(user, OutputArbiter.HANDOFF_MILLIS, fx, play);
    }

    void stop() {
        wanted = false;
        arbiter.sharedStopped(user);
    }

    void observed(PlaybackStatus status) {
        if (TERMINAL.contains(status)) {
            wanted = false;
            arbiter.sharedStopped(user);
        } else if (SILENT.contains(status) && !wanted) {
            arbiter.sharedStopped(user);
        }
    }
}
