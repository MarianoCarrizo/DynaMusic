package com.marianocarrizo.dynamusic.ambiance;

import com.marianocarrizo.dynamusic.service.AppDataFolder;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Motor de bake de boomerang: sondeo ffmpeg, caché SHA-1, invocación asíncrona y evicción.
 * Sin dependencias de JavaFX — los callbacks llegan al hilo del fork-join pool;
 * el llamador es responsable de marshall a Platform.runLater.
 */
public final class BoomerangBaker {

    private static final int  MAX_CACHE_ENTRIES    = 20;
    private static final long BAKE_TIMEOUT_SECONDS = 60;
    private static final int  STDERR_TAIL_BYTES    = 2048;

    // --- Sondeo estático de ffmpeg ---

    public static boolean isFfmpegAvailable() {
        return FfmpegLocator.isAvailable();
    }

    // --- Directorio de caché ---

    private Path cacheDir() throws IOException {
        return Files.createDirectories(AppDataFolder.child("boomerang-cache"));
    }

    // --- Clave de caché ---

    /**
     * Calcula la ruta del archivo de caché para la combinación (fuente, startMs, endMs).
     * Clave: SHA-1(srcAbsPath + ":" + startMs + ":" + endMs) en hexadecimal.
     */
    public Path cacheKeyPath(String srcAbsPath, long startMs, long endMs) {
        try {
            String input = srcAbsPath + ":" + startMs + ":" + endMs;
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = sha1.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return cacheDir().resolve(hex + ".mp4");
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 siempre está disponible en java.security
            throw new IllegalStateException("SHA-1 not available", e);
        } catch (IOException e) {
            throw new RuntimeException("Cannot resolve cache directory", e);
        }
    }

    /**
     * Retorna true si ya existe un archivo de caché válido para los parámetros dados.
     */
    public boolean isCached(String srcAbsPath, long startMs, long endMs) {
        return Files.exists(cacheKeyPath(srcAbsPath, startMs, endMs));
    }

    /**
     * Retorna true si la ruta apunta DENTRO del directorio de caché de boomerang.
     * Los archivos de caché son estado derivado: jamás deben persistirse como
     * identidad del fondo (backgroundImagePath).
     */
    public boolean isCachePath(String absPath) {
        if (absPath == null) return false;
        try {
            return Paths.get(absPath).toAbsolutePath().normalize()
                    .startsWith(cacheDir().toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    // --- Bake asíncrono ---

    /**
     * Inicia el bake en un hilo del fork-join pool.
     * Los callbacks se invocan en el hilo del pool — el llamador debe usar Platform.runLater.
     *
     * @param srcAbsPath ruta absoluta del video fuente
     * @param startMs    inicio del segmento en milisegundos
     * @param endMs      fin del segmento en milisegundos
     * @param onSuccess  recibe la ruta del archivo bakeado al completar con éxito
     * @param onFailure  recibe un mensaje de error (stderr tail o descripción)
     */
    public void bakeAsync(String srcAbsPath, long startMs, long endMs,
                          Consumer<Path> onSuccess, Consumer<String> onFailure) {
        Path outPath = cacheKeyPath(srcAbsPath, startMs, endMs);

        // Formatear tiempos con Locale.ROOT — CRÍTICO: es-AR usa coma decimal, ffmpeg requiere punto
        String startSec = String.format(Locale.ROOT, "%.3f", startMs / 1000.0);
        String endSec   = String.format(Locale.ROOT, "%.3f", endMs   / 1000.0);

        Optional<String> ffmpeg = FfmpegLocator.executable();
        if (ffmpeg.isEmpty()) {
            onFailure.accept("ffmpeg is not available");
            return;
        }

        List<String> cmd = List.of(
                ffmpeg.get(), "-y",
                "-ss", startSec,
                "-to", endSec,
                "-i", srcAbsPath,
                // -filter_complex (no -vf): las salidas etiquetadas + -map requieren un grafo complejo
                "-filter_complex", "[0:v]split[fwd][rev];[rev]reverse[rev_out];[fwd][rev_out]concat=n=2:v=1:a=0[out]",
                "-map", "[out]",
                "-an",
                "-c:v", "libx264",
                "-crf", "22",
                "-preset", "fast",
                "-movflags", "+faststart",
                outPath.toString()
        );

        CompletableFuture.runAsync(() -> {
            Process proc = null;
            try {
                proc = new ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start();

                // Drenar y capturar los últimos STDERR_TAIL_BYTES para diagnóstico
                String stderrTail = drainAndTail(proc.getInputStream(), STDERR_TAIL_BYTES);

                boolean finished = proc.waitFor(BAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    proc.destroyForcibly();
                    onFailure.accept("timeout after " + BAKE_TIMEOUT_SECONDS + "s");
                    return;
                }

                if (proc.exitValue() == 0) {
                    evictOldEntries();
                    onSuccess.accept(outPath);
                } else {
                    onFailure.accept(stderrTail.isBlank() ? "ffmpeg exit " + proc.exitValue() : stderrTail);
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                onFailure.accept(e.getMessage());
            } finally {
                if (proc != null && proc.isAlive()) proc.destroyForcibly();
            }
        }).exceptionally(ex -> {
            onFailure.accept(ex.getMessage());
            return null;
        });
    }

    // --- Evicción por conteo ---

    /**
     * Elimina los archivos más antiguos del directorio de caché cuando supera MAX_CACHE_ENTRIES.
     * Best-effort: las IOException se ignoran silenciosamente.
     */
    private void evictOldEntries() {
        try {
            Path dir = cacheDir();
            List<Path> files;
            try (Stream<Path> stream = Files.list(dir)) {
                files = stream
                        .filter(p -> p.toString().endsWith(".mp4"))
                        .collect(Collectors.toList());
            }
            if (files.size() <= MAX_CACHE_ENTRIES) return;

            files.sort(Comparator.comparing(p -> {
                try {
                    return Files.getLastModifiedTime((Path) p);
                } catch (IOException e) {
                    return FileTime.fromMillis(0);
                }
            }));

            int toDelete = files.size() - MAX_CACHE_ENTRIES;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(files.get(i));
                } catch (IOException ignored) {
                    // Best-effort
                }
            }
        } catch (IOException ignored) {
            // Best-effort
        }
    }

    // --- Utilidad interna ---

    /**
     * Lee el InputStream hasta EOF y retorna los últimos {@code maxBytes} como String.
     */
    private static String drainAndTail(InputStream in, int maxBytes) {
        try {
            byte[] all = in.readAllBytes();
            if (all.length <= maxBytes) return new String(all, StandardCharsets.UTF_8);
            return new String(all, all.length - maxBytes, maxBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
