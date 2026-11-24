package com.marianocarrizo.dynamusic.library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IndexCacheFile implements IndexStore {

    private final Path file;

    public IndexCacheFile(Path file) {
        this.file = file;
    }

    @Override
    public Map<String, TrackEntry> load() {
        Map<String, TrackEntry> entries = new LinkedHashMap<>();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (TrackEntry entry : IndexCacheCodec.decode(text)) entries.put(entry.path(), entry);
        } catch (IOException | RuntimeException unreadable) {
            entries.clear();
        }
        return entries;
    }

    @Override
    public void save(Collection<TrackEntry> entries) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temporary, IndexCacheCodec.encode(entries), StandardCharsets.UTF_8);
            replace(temporary);
        } catch (IOException | RuntimeException failed) {
            System.err.println("Could not save the library index: " + failed.getMessage());
        }
    }

    private void replace(Path temporary) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
