package com.marianocarrizo.dynamusic.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class FfmpegLocator {

    static final String BUNDLED_DIR = "ffmpeg";
    private static final long VERSION_QUERY_TIMEOUT_SECONDS = 3;

    private FfmpegLocator() {}

    private static final Path BUNDLED = detect();

    public static Optional<String> executable() {
        if (BUNDLED != null) return Optional.of(BUNDLED.resolve(binaryName(osName())).toString());
        return ExecutableResolver.find("ffmpeg").map(Path::toString);
    }

    public static boolean isAvailable() {
        return AvailabilityProbe.RESULT;
    }

    static boolean answersVersionQuery(Optional<String> executable) {
        if (executable.isEmpty()) return false;
        try {
            Process process = new ProcessBuilder(executable.get(), "-version").redirectErrorStream(true).start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (!process.waitFor(VERSION_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static Optional<Path> directory() {
        if (BUNDLED != null) return Optional.of(BUNDLED);
        return ExecutableResolver.find("ffmpeg").map(Path::getParent);
    }

    private static Path detect() {
        Path root = installRoot(System.getProperty("jpackage.app-path"), System.getProperty("java.home"));
        return findBundled(root, osName(), Files::isRegularFile).orElse(null);
    }

    private static String osName() {
        return System.getProperty("os.name", "");
    }

    static Path installRoot(String appPathProp, String javaHome) {
        if (appPathProp != null && !appPathProp.isBlank()) {
            Path parent = Paths.get(appPathProp).toAbsolutePath().getParent();
            if (parent != null) return parent;
        }
        if (javaHome != null && !javaHome.isBlank()) {
            Path home = Paths.get(javaHome).toAbsolutePath();
            if (home.getFileName() != null && home.getFileName().toString().equals("runtime")) return home.getParent();
        }
        return null;
    }

    static Optional<Path> findBundled(Path installRoot, String osName, Predicate<Path> isFile) {
        if (installRoot == null) return Optional.empty();
        Path dir = installRoot.resolve(BUNDLED_DIR);
        return isFile.test(dir.resolve(binaryName(osName))) ? Optional.of(dir) : Optional.empty();
    }

    static String binaryName(String osName) {
        return YtDlpManager.isWindows(osName) ? "ffmpeg.exe" : "ffmpeg";
    }

    private static final class AvailabilityProbe {
        static final boolean RESULT = answersVersionQuery(executable());
    }
}
