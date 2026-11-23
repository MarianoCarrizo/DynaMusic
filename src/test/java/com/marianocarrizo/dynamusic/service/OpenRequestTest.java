package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRequestTest {

    @TempDir Path dir;

    private File audio(String name) throws IOException {
        return Files.createFile(dir.resolve(name)).toFile();
    }

    private static byte[] message(String... lines) {
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void roundTripsPlayableFiles() throws IOException {
        File song = audio("song.mp3");
        File other = audio("other.flac");

        Optional<OpenRequest> decoded = OpenRequest.decode(new OpenRequest(List.of(song, other)).encode());

        assertEquals(List.of(song.getAbsoluteFile(), other.getAbsoluteFile()), decoded.orElseThrow().files());
    }

    @Test
    void acceptsEveryRegistryFormat() throws IOException {
        List<File> files = new ArrayList<>();
        for (String extension : AudioFormat.allExtensions()) files.add(audio("song." + extension));

        OpenRequest decoded = OpenRequest.decode(new OpenRequest(files).encode()).orElseThrow();

        assertEquals(Math.min(files.size(), OpenRequest.MAX_FILES), decoded.files().size());
    }

    @Test
    void emptyRequestRoundTripsSoASecondLaunchCanJustFocusTheWindow() {
        Optional<OpenRequest> decoded = OpenRequest.decode(new OpenRequest(List.of()).encode());

        assertTrue(decoded.orElseThrow().files().isEmpty());
    }

    @Test
    void rejectsMessagesWithoutTheExpectedHeader() throws IOException {
        File song = audio("song.mp3");

        assertTrue(OpenRequest.decode(message("HELLO", song.getAbsolutePath())).isEmpty());
        assertTrue(OpenRequest.decode(message(song.getAbsolutePath())).isEmpty());
        assertTrue(OpenRequest.decode(message()).isEmpty());
    }

    @Test
    void rejectsNullAndOversizedMessages() {
        assertTrue(OpenRequest.decode(null).isEmpty());
        assertTrue(OpenRequest.decode(new byte[OpenRequest.MAX_BYTES + 1]).isEmpty());
    }

    @Test
    void dropsPathsThatAreNotExistingAudioFiles() throws IOException {
        File song = audio("song.wav");
        File notAudio = audio("notes.txt");
        File missing = dir.resolve("missing.mp3").toFile();

        OpenRequest decoded = OpenRequest.decode(message(OpenRequest.HEADER, notAudio.getAbsolutePath(),
                missing.getAbsolutePath(), dir.toString(), song.getAbsolutePath())).orElseThrow();

        assertEquals(List.of(song.getAbsoluteFile()), decoded.files());
    }

    @Test
    void ignoresRelativeMalformedAndControlCharacterPaths() throws IOException {
        File song = audio("song.mp3");

        OpenRequest decoded = OpenRequest.decode(message(OpenRequest.HEADER, "song.mp3", "",
                song.getAbsolutePath() + "\u0000", "<>|?*", song.getAbsolutePath())).orElseThrow();

        assertEquals(List.of(song.getAbsoluteFile()), decoded.files());
    }

    @Test
    void ignoresOverlongPaths() {
        String overlong = dir.resolve("a".repeat(OpenRequest.MAX_PATH_LENGTH) + ".mp3").toString();

        OpenRequest decoded = OpenRequest.decode(message(OpenRequest.HEADER, overlong)).orElseThrow();

        assertTrue(decoded.files().isEmpty());
    }

    @Test
    void capsTheNumberOfFiles() throws IOException {
        List<String> lines = new ArrayList<>(List.of(OpenRequest.HEADER));
        for (int i = 0; i < OpenRequest.MAX_FILES + 5; i++) {
            lines.add(audio("track" + i + ".mp3").getAbsolutePath());
        }

        OpenRequest decoded = OpenRequest.decode(message(lines.toArray(String[]::new))).orElseThrow();

        assertEquals(OpenRequest.MAX_FILES, decoded.files().size());
    }

    @Test
    void launchArgumentsKeepOnlyExistingAudioFiles() throws IOException {
        File song = audio("song.flac");

        OpenRequest request = OpenRequest.fromPaths(List.of("--flag", dir.resolve("none.mp3").toString(),
                song.getAbsolutePath()));

        assertEquals(List.of(song.getAbsoluteFile()), request.files());
    }

    @Test
    void extensionMatchIsCaseInsensitive() throws IOException {
        File song = audio("LOUD.MP3");

        assertEquals(List.of(song.getAbsoluteFile()),
                OpenRequest.fromPaths(List.of(song.getAbsolutePath())).files());
    }

    @Test
    void rejectsNetworkAndDevicePathsBeforeTouchingTheFilesystem() {
        List<String> remote = List.of("\\\\server\\share\\song.mp3", "//server/share/song.mp3",
                "\\\\?\\C:\\song.mp3", "\\\\.\\PhysicalDrive0\\song.mp3", "\\/server/share/song.mp3",
                "/\\server\\share\\song.mp3");

        for (String path : remote) {
            assertTrue(OpenRequest.isNetworkOrDevicePath(path), path);
            assertTrue(OpenRequest.decode(message(OpenRequest.HEADER, path)).orElseThrow().files().isEmpty(), path);
            assertTrue(OpenRequest.fromPaths(List.of(path)).files().isEmpty(), path);
        }
    }

    @Test
    void localPathsAreNotTreatedAsNetworkPaths() throws IOException {
        File song = audio("song.mp3");

        assertFalse(OpenRequest.isNetworkOrDevicePath(song.getAbsolutePath()));
        assertFalse(OpenRequest.isNetworkOrDevicePath("a"));
        assertFalse(OpenRequest.isNetworkOrDevicePath("/home/user/song.mp3"));
    }
}
