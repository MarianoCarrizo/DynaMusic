package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ExtensionLiteralScanTest {

    private static final Path MAIN_SOURCES = Paths.get("src", "main", "java");
    private static final String REGISTRY_FILE = "AudioFormat.java";

    @Test
    void noExtensionLiteralsLiveOutsideTheRegistry() throws IOException {
        String extensions = String.join("|", AudioFormat.allExtensions());
        Pattern literal = Pattern.compile(
                "\"\\.?(" + extensions + ")\"|\\.(" + extensions + ")\"", Pattern.CASE_INSENSITIVE);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals(REGISTRY_FILE)) continue;
                for (String line : Files.readAllLines(file)) {
                    if (literal.matcher(line).find()) offenders.add(file.getFileName() + ": " + line.strip());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "extension literals outside AudioFormat: " + offenders);
    }
}
