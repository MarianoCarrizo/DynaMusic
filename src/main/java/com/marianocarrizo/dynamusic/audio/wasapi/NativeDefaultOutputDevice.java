package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.DefaultOutputDevice;
import java.util.Locale;
import java.util.Optional;

public final class NativeDefaultOutputDevice implements DefaultOutputDevice {

    private static final String WINDOWS = "windows";

    public static DefaultOutputDevice system() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith(WINDOWS);
        return windows ? new NativeDefaultOutputDevice() : DefaultOutputDevice.unavailable();
    }

    @Override
    public Optional<String> currentId() {
        try (Endpoint endpoint = Endpoint.open()) {
            return Optional.of(endpoint.id()).filter(id -> !id.isBlank());
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }
}
