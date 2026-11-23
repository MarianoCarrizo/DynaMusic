package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.tags.CoverReader;
import com.marianocarrizo.dynamusic.tags.TagFormat;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
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

    public void load(File track, Consumer<Optional<Image>> onResult) {
        final int request = requests.begin();
        if (track == null) {
            onResult.accept(Optional.empty());
            return;
        }
        final boolean[] embeddedShown = {false};
        showFolderCover(track.getAbsoluteFile().getParentFile(), request, embeddedShown, onResult);
        TagFormat format = AudioFormat.tagFormatOf(track);
        if (format != TagFormat.NONE) readEmbeddedArt(track, format, request, embeddedShown, onResult);
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

    private void readEmbeddedArt(File track, TagFormat format, int request, boolean[] embeddedShown,
                                 Consumer<Optional<Image>> onResult) {
        io.submit(() -> {
            Optional<Image> image = CoverReader.read(track, format).map(CoverArtLoader::decode)
                    .filter(decoded -> !decoded.isError());
            if (image.isEmpty()) return;
            Platform.runLater(() -> {
                if (!requests.isCurrent(request)) return;
                embeddedShown[0] = true;
                onResult.accept(image);
            });
        });
    }

    private static Image decode(byte[] bytes) {
        return new Image(new ByteArrayInputStream(bytes), DECODE_PIXELS, DECODE_PIXELS, true, true);
    }
}
