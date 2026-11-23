package com.marianocarrizo.dynamusic.service;

import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.scene.image.Image;
import javafx.scene.media.Media;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class CoverArtLoader {

    private static final int DECODE_PIXELS = 112;
    private static final int CACHE_SIZE = 32;
    private static final String EMBEDDED_IMAGE_KEY = "image";

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cover-art");
        t.setDaemon(true);
        return t;
    });
    private final LoadSequence requests = new LoadSequence();
    private final Map<File, Optional<Image>> folderCovers = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<File, Optional<Image>> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private Media parsingMedia;

    public void load(File track, Consumer<Optional<Image>> onResult) {
        final int request = requests.begin();
        if (track == null) {
            onResult.accept(Optional.empty());
            return;
        }
        final boolean[] embeddedShown = {false};
        showFolderCover(track.getAbsoluteFile().getParentFile(), request, embeddedShown, onResult);
        if (CoverFiles.mayHaveEmbeddedArt(track)) readEmbeddedArt(track, request, embeddedShown, onResult);
    }

    private void showFolderCover(File folder, int request, boolean[] embeddedShown,
                                 Consumer<Optional<Image>> onResult) {
        Optional<Image> cached = folder == null ? Optional.empty() : folderCovers.get(folder);
        if (cached != null) {
            onResult.accept(cached);
            return;
        }
        io.submit(() -> {
            Optional<File> file = CoverFiles.find(folder);
            Platform.runLater(() -> {
                Optional<Image> image = file.map(f -> new Image(f.toURI().toString(), DECODE_PIXELS, DECODE_PIXELS,
                        true, true, true));
                if (folder != null) folderCovers.put(folder, image);
                if (requests.isCurrent(request) && !embeddedShown[0]) onResult.accept(image);
            });
        });
    }

    private void readEmbeddedArt(File track, int request, boolean[] embeddedShown,
                                 Consumer<Optional<Image>> onResult) {
        try {
            Media media = new Media(track.toURI().toString());
            parsingMedia = media;
            Consumer<Object> deliver = value -> {
                if (!(value instanceof Image image) || !requests.isCurrent(request)) return;
                embeddedShown[0] = true;
                onResult.accept(Optional.of(image));
            };
            media.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
                if (change.wasAdded() && EMBEDDED_IMAGE_KEY.equals(change.getKey())) {
                    deliver.accept(change.getValueAdded());
                }
            });
            deliver.accept(media.getMetadata().get(EMBEDDED_IMAGE_KEY));
        } catch (RuntimeException e) {
            parsingMedia = null;
        }
    }
}
