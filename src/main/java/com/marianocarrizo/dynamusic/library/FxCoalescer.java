package com.marianocarrizo.dynamusic.library;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class FxCoalescer<T> {

    private final Executor fx;
    private final Consumer<T> sink;
    private final AtomicReference<T> latest = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    FxCoalescer(Executor fx, Consumer<T> sink) {
        this.fx = fx;
        this.sink = sink;
    }

    void publish(T value) {
        latest.set(value);
        if (scheduled.compareAndSet(false, true)) fx.execute(this::deliver);
    }

    void clear() {
        latest.set(null);
    }

    private void deliver() {
        scheduled.set(false);
        T value = latest.getAndSet(null);
        if (value != null) sink.accept(value);
    }
}
