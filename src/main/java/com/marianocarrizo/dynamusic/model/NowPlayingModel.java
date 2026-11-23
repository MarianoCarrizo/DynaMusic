package com.marianocarrizo.dynamusic.model;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

public final class NowPlayingModel {

    private final ReadOnlyObjectWrapper<NowPlaying> snapshot = new ReadOnlyObjectWrapper<>(NowPlaying.NONE);

    public void show(NowPlaying next) {
        if (!snapshot.get().equals(next)) snapshot.set(next);
    }

    public NowPlaying get() {
        return snapshot.get();
    }

    public ReadOnlyObjectProperty<NowPlaying> snapshotProperty() {
        return snapshot.getReadOnlyProperty();
    }
}
