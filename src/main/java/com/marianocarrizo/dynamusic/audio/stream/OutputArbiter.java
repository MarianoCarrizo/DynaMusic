package com.marianocarrizo.dynamusic.audio.stream;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class OutputArbiter {

    public static final long HANDOFF_MILLIS = 150;

    private static final OutputArbiter PROCESS = new OutputArbiter();

    private final Object lock = new Object();
    private final Set<Object> sharedUsers = Collections.newSetFromMap(new IdentityHashMap<>());
    private Object holder;

    public static OutputArbiter process() {
        return PROCESS;
    }

    public boolean sharedInUse() {
        synchronized (lock) {
            return !sharedUsers.isEmpty();
        }
    }

    public boolean holdsExclusive() {
        synchronized (lock) {
            return holder != null;
        }
    }

    public boolean claimExclusive(Object claimant, long timeoutMillis) {
        synchronized (lock) {
            if (!sharedUsers.isEmpty()) return false;
            if (!await(() -> holder == null || holder == claimant, timeoutMillis)) return false;
            if (!sharedUsers.isEmpty()) return false;
            holder = claimant;
            return true;
        }
    }

    public void releaseExclusive(Object claimant) {
        synchronized (lock) {
            if (holder != claimant) return;
            holder = null;
            lock.notifyAll();
        }
    }

    public boolean sharedStarting(Object user, long timeoutMillis) {
        synchronized (lock) {
            sharedUsers.add(user);
            lock.notifyAll();
            return await(() -> holder == null, timeoutMillis);
        }
    }

    public void sharedStopped(Object user) {
        synchronized (lock) {
            sharedUsers.remove(user);
        }
    }

    private boolean await(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            while (!condition.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            }
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return condition.getAsBoolean();
        }
    }
}
