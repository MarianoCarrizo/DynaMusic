package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InstallerRegistrationTest {

    private static final Path SCRIPT = Paths.get("installer", "DynaMusic.iss");
    private static final Pattern OPEN_WITH = Pattern.compile("Classes\\\\\\.([a-z0-9]+)\\\\OpenWithProgids");
    private static final Pattern SUPPORTED_TYPE = Pattern.compile("SupportedTypes\"; ValueType: string; ValueName: \"\\.([a-z0-9]+)\"");

    private static Set<String> matches(String text, Pattern pattern) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) found.add(matcher.group(1));
        return found;
    }

    private static String script() throws IOException {
        return Files.readString(SCRIPT, StandardCharsets.UTF_8);
    }

    private static Set<String> registryExtensions() {
        return new TreeSet<>(AudioFormat.allExtensions());
    }

    @Test
    void openWithListsExactlyTheRegistryExtensions() throws IOException {
        assertEquals(registryExtensions(), matches(script(), OPEN_WITH));
    }

    @Test
    void supportedTypesListExactlyTheRegistryExtensions() throws IOException {
        assertEquals(registryExtensions(), matches(script(), SUPPORTED_TYPE));
    }

    @Test
    void everyExtensionIsRemovedOnUninstall() throws IOException {
        List<String> openWithLines = script().lines().filter(line -> line.contains("OpenWithProgids")).toList();

        assertEquals(registryExtensions().size(), openWithLines.size());
        assertTrue(openWithLines.stream().allMatch(line -> line.contains("uninsdeletevalue")));
    }
}
