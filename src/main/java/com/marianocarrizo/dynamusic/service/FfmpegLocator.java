package com.marianocarrizo.dynamusic.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class FfmpegLocator {

    static final String BUNDLED_DIR = "ffmpeg";
    private static final long VERSION_QUERY_TIMEOUT_MILLIS = 3000;
    private static final long AWAIT_TIMEOUT_SECONDS = 6;
    private static final String PROBE_THREAD_NAME = "ffmpeg-probe";
    private static final CompletableFuture<Boolean> PROBE = new CompletableFuture<>();
    private static final AtomicBoolean PROBE_STARTED = new AtomicBoolean();

    private FfmpegLocator() {}

    private static final Path BUNDLED = detect();

    public static Optional<String> executable() {
        if (BUNDLED != null) return Optional.of(BUNDLED.resolve(binaryName(osName())).toString());
        return ExecutableResolver.find("ffmpeg").map(Path::toString);
    }

    public static boolean isAvailable() {
        startProbe();
        return !PROBE.isDone() || PROBE.getNow(true);
    }

    public static boolean awaitAvailability() {
        startProbe();
        try {
            return PROBE.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException failed) {
            return false;
        }
    }

    public static void onResolved(Runnable listener) {
        startProbe();
        PROBE.thenRun(listener);
    }

    private static void startProbe() {
        if (!PROBE_STARTED.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> PROBE.complete(answersVersionQuery(executable(),
                VERSION_QUERY_TIMEOUT_MILLIS)), PROBE_THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    static boolean answersVersionQuery(Optional<String> executable, long timeoutMillis) {
        if (executable.isEmpty()) return false;
        return answers(List.of(executable.get(), "-version"), timeoutMillis);
    }

    static boolean answers(List<String> command, long timeoutMillis) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Process running = process;
            Thread.ofVirtual().start(() -> drain(running));
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) return false;
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null) process.destroyForcibly();
        }
    }

    private static void drain(Process process) {
        try {
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
        } catch (IOException closed) {
            return;
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
}
