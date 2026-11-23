package com.marianocarrizo.dynamusic.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class NowPlayingModel {

    public static final String NO_TITLE = "—";

    private final ObjectProperty<NowPlayingSource> source = new SimpleObjectProperty<>(NowPlayingSource.NONE);
    private final StringProperty sourceName = new SimpleStringProperty("");
    private final StringProperty title = new SimpleStringProperty(NO_TITLE);
    private final BooleanProperty playing = new SimpleBooleanProperty(false);

    public void show(NowPlayingSource newSource, String newSourceName, boolean isPlaying) {
        source.set(newSource);
        sourceName.set(newSource.isNamed() && newSourceName != null ? newSourceName : "");
        playing.set(newSource != NowPlayingSource.NONE && isPlaying);
    }

    public NowPlayingSource getSource() {
        return source.get();
    }

    public String getSourceName() {
        return sourceName.get();
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public ReadOnlyObjectProperty<NowPlayingSource> sourceProperty() {
        return source;
    }

    public ReadOnlyStringProperty sourceNameProperty() {
        return sourceName;
    }

    public ReadOnlyBooleanProperty playingProperty() {
        return playing;
    }

    public StringProperty titleProperty() {
        return title;
    }
}
