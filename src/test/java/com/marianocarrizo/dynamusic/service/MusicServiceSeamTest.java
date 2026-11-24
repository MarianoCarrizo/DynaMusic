package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class MusicServiceSeamTest {

    private static final Path SOURCE = Paths.get(
            "src", "main", "java", "com", "marianocarrizo", "dynamusic", "service", "MusicService.java");

    @Test
    void musicServiceImportsNoJavaFxMediaType() throws IOException {
        boolean importsMedia = Files.readAllLines(SOURCE).stream()
                .anyMatch(line -> line.startsWith("import javafx.scene.media."));

        assertFalse(importsMedia);
    }
}
