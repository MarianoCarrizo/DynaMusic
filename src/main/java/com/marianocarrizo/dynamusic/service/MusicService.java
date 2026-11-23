package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.audio.AudioFormat;
import com.marianocarrizo.dynamusic.audio.AudioServices;
import com.marianocarrizo.dynamusic.audio.PreparedAudio;
import com.marianocarrizo.dynamusic.audio.UnplayableAudioException;
import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.tags.TrackTagStore;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

public class MusicService {

    // =========================================================================
    // T-07: Enum de modo de reproducción
    // =========================================================================

    /** Modo de reproducción activo. Solo un modo emite audio a la vez. */
    public enum PlaybackMode {
        MOOD(NowPlayingSource.MOOD),
        PLAYLIST(NowPlayingSource.PLAYLIST),
        ON_DEMAND(NowPlayingSource.ON_DEMAND);

        private final NowPlayingSource source;

        PlaybackMode(NowPlayingSource source) {
            this.source = source;
        }

        public NowPlayingSource source() {
            return source;
        }
    }

    // =========================================================================
    // Campos existentes (mood players)
    // =========================================================================

    // Mapa principal de reproductores por estado de ánimo
    private final Map<TrackType, MediaPlayer> players;

    // Nombres de archivo por tipo, para actualizar el label "ahora sonando"
    private final Map<TrackType, File> trackFiles;

    // Volumen RMS calculado por pista — necesario para restaurar al hacer fade-in
    private final Map<TrackType, Double> trackGains;

    private final Map<TrackType, Integer> loadSeq = new EnumMap<>(TrackType.class);

    // Timeline de fade activo (mood o playlist) — se cancela si el usuario actúa antes
    private Timeline activeFade;

    private TrackType currentTrack;
    private boolean loopEnabled;
    private boolean previewLoopEnabled;
    private BiConsumer<Duration, Duration> activeProgressListener = (current, total) -> { };
    private Runnable onPlaybackChanged = () -> { };
    private MediaPlayer fadingOutPlayer;
    private File previewFile;
    private File playlistFile;
    private File onDemandFile;
    private String onDemandTitle;

    // Reproductor dedicado a preescucha — nunca entra al mapa 'players'
    private MediaPlayer previewPlayer;
    private final AudioFocus audioFocus =
            new AudioFocus(() -> previewPlayer != null, this::pausePreviewPlayer, this::pauseModePlayers);

    // Amplitud de audio: escrita en el hilo del callback del espectro, leída por la UI
    private final AudioAnalyzer audioAnalyzer = new AudioAnalyzer();

    private static final String DEFAULT_FAILURE_MESSAGE = "This file could not be played.";
    private static final int MAX_CONSECUTIVE_PLAYLIST_FAILURES = 5;

    private final TrackTagStore tags = new TrackTagStore(AudioFormat::tagFormatOf, Platform::runLater);
    private final AudioServices audio;
    private final PendingLoads pending = new PendingLoads(System::currentTimeMillis);
    private int playlistPendingTicket = PendingLoads.NO_TICKET;
    private int consecutivePlaylistFailures;
    private CompletableFuture<PreparedAudio> playlistPrep = CompletableFuture.completedFuture(null);

    // =========================================================================
    // T-07: Campos del modo playlist
    // =========================================================================

    // Modo activo; por defecto MOOD al arrancar
    private PlaybackMode mode = PlaybackMode.MOOD;

    // Reproductor único del modo playlist — un solo MediaPlayer activo a la vez
    private MediaPlayer playlistPlayer = null;

    // Ganancia RMS de la pista actual del playlist (para restaurar después del fade)
    private double playlistGain = 1.0;

    // Volumen maestro global [0.0-1.0]; multiplicador sobre las ganancias RMS
    private double masterVolume = 1.0;

    // Ganancia RMS raw de la preescucha activa (necesaria para live-reapply del volumen maestro)
    private double previewGain = 1.0;

    private MediaPlayer onDemandPlayer = null;
    private double onDemandGain = 1.0;
    private boolean onDemandLoop = false;
    private int onDemandSeq = 0;
    private final LoadSequence playlistLoads = new LoadSequence();

    // Callback que el controlador suministra para avanzar al siguiente track
    private Runnable onPlaylistEnd = null;
    private Runnable onPlaylistTrackFailed = null;

    // =========================================================================
    // Constructor
    // =========================================================================

    public MusicService() {
        this(AudioServices.standard());
    }

    public MusicService(AudioServices audio) {
        this.audio = audio;
        this.players = new HashMap<>();
        this.trackFiles = new EnumMap<>(TrackType.class);
        this.trackGains = new EnumMap<>(TrackType.class);
        this.currentTrack = null;
        this.loopEnabled = false;
        this.previewLoopEnabled = false;
        this.previewPlayer = null;
        tags.onLoaded(this::notifyPlaybackChanged);
    }

    public TrackTagStore tags() {
        return tags;
    }

    // =========================================================================
    // Volumen maestro global
    // =========================================================================

    /** Único punto de escala: toda ganancia RAW debe pasar por aquí antes de llegar a setVolume. */
    private double effectiveGain(double rawGain) {
        return rawGain * masterVolume;
    }

    /**
     * Actualiza el volumen maestro y lo reaplica en tiempo real a todos los reproductores activos.
     * No toca el reproductor de ambiance (INV-5).
     */
    public void setMasterVolume(double v) {
        this.masterVolume = Math.max(0.0, Math.min(1.0, v));
        if (currentTrack != null && players.containsKey(currentTrack)) {
            players.get(currentTrack).setVolume(
                effectiveGain(trackGains.getOrDefault(currentTrack, 1.0)));
        }
        if (playlistPlayer != null) {
            playlistPlayer.setVolume(effectiveGain(playlistGain));
        }
        if (previewPlayer != null) {
            previewPlayer.setVolume(effectiveGain(previewGain));
        }
        if (onDemandPlayer != null) {
            onDemandPlayer.setVolume(effectiveGain(onDemandGain));
        }
    }

    public double getMasterVolume() {
        return masterVolume;
    }

    // =========================================================================
    // Propiedad nowPlaying + amplitud
    // =========================================================================

    public void setOnPlaybackChanged(Runnable listener) {
        onPlaybackChanged = listener == null ? () -> { } : listener;
    }

    public void setActiveProgressListener(BiConsumer<Duration, Duration> listener) {
        activeProgressListener = listener == null ? (current, total) -> { } : listener;
    }

    public void reportActiveProgress() {
        reportProgress(activePlayer());
    }

    public NowPlayingSource activeSource() {
        return audioFocus.previewSoundedLast() ? NowPlayingSource.PREVIEW : mode.source();
    }

    public boolean hasPlaylistTrack() {
        return playlistFile != null;
    }

    public PlaybackState playbackState() {
        return new PlaybackState(mode, audioFocus.previewSoundedLast(), currentTrack,
                previewSounding(), moodSounding(), playlistSounding(), onDemandSounding(),
                pending.current(tags::peek));
    }

    private SoundingTrack previewSounding() {
        if (previewPlayer == null || previewFile == null) return SoundingTrack.ABSENT;
        return SoundingTrack.of(previewFile, tags.peek(previewFile), isPreviewPlaying());
    }

    private SoundingTrack moodSounding() {
        File file = currentTrack == null || !players.containsKey(currentTrack) ? null : trackFiles.get(currentTrack);
        return file == null ? SoundingTrack.ABSENT : SoundingTrack.of(file, tags.peek(file), isPlaying());
    }

    private SoundingTrack playlistSounding() {
        return playlistFile == null ? SoundingTrack.ABSENT : SoundingTrack.of(playlistFile, tags.peek(playlistFile), isPlaylistPlaying());
    }

    private SoundingTrack onDemandSounding() {
        if (onDemandFile == null) return SoundingTrack.ABSENT;
        if (onDemandTitle == null) return SoundingTrack.of(onDemandFile, tags.peek(onDemandFile), isOnDemandPlaying());
        return SoundingTrack.of(onDemandTitle, onDemandFile, tags.peek(onDemandFile), isOnDemandPlaying());
    }

    private void notifyPlaybackChanged() {
        onPlaybackChanged.run();
    }

    private int beginPending(NowPlayingSource source, TrackType moodType, File file, String title) {
        int ticket = pending.begin(source, moodType, file, title);
        notifyPlaybackChanged();
        return ticket;
    }

    private void finishPending(int ticket) {
        if (pending.finish(ticket)) notifyPlaybackChanged();
    }

    private void failPending(int ticket, Throwable error) {
        if (!pending.fail(ticket, failureMessage(error))) return;
        notifyPlaybackChanged();
        PauseTransition hold = new PauseTransition(Duration.millis(PendingLoads.FAILURE_DISPLAY_MILLIS));
        hold.setOnFinished(event -> notifyPlaybackChanged());
        hold.play();
    }

    private static String failureMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof UnplayableAudioException ? cause.getMessage() : DEFAULT_FAILURE_MESSAGE;
    }

    private void observeStatus(MediaPlayer player) {
        player.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus != MediaPlayer.Status.PLAYING && player == fadingOutPlayer) fadingOutPlayer = null;
            notifyPlaybackChanged();
        });
    }

    private boolean isSounding(MediaPlayer player) {
        return player != null && player != fadingOutPlayer && player.getStatus() == MediaPlayer.Status.PLAYING;
    }

    private MediaPlayer activePlayer() {
        if (audioFocus.previewSoundedLast()) return previewPlayer;
        return switch (mode) {
            case MOOD -> currentTrack == null ? null : players.get(currentTrack);
            case PLAYLIST -> playlistPlayer;
            case ON_DEMAND -> onDemandPlayer;
        };
    }

    private void emitProgress(MediaPlayer player) {
        if (player == activePlayer()) reportProgress(player);
    }

    private void reportProgress(MediaPlayer player) {
        if (player == null || pending.isActive()) return;
        Duration total = player.getTotalDuration();
        if (total != null && !total.isUnknown()) activeProgressListener.accept(player.getCurrentTime(), total);
    }

    public AudioFeatures getAudioFeatures() {
        return audioAnalyzer.current();
    }

    /**
     * Adjunta el listener de espectro de audio a un reproductor.
     * Es seguro llamar desde cualquier hilo.
     */
    private void attachSpectrumListener(MediaPlayer player) {
        audioAnalyzer.reset();
        player.setAudioSpectrumInterval(0.025);
        player.setAudioSpectrumNumBands(AudioAnalyzer.NUM_BANDS);
        player.setAudioSpectrumThreshold(-75);
        player.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
            audioAnalyzer.process(magnitudes, timestamp);
        });
    }

    // =========================================================================
    // T-07: setPlaybackMode — cambio de modo con teardown del modo saliente
    // =========================================================================

    /**
     * Cambia el modo de reproducción activo.
     * Debe invocarse desde el hilo FX.
     */
    public void setPlaybackMode(PlaybackMode target) {
        // Advertencia si se llama fuera del hilo FX (T-17)
        if (!Platform.isFxApplicationThread()) {
            System.err.println("ADVERTENCIA: setPlaybackMode fue invocado fuera del hilo FX");
        }
        if (target != PlaybackMode.ON_DEMAND) onDemandSeq++;
        if (mode == target) return;

        // --- Teardown del modo saliente ---
        if (mode == PlaybackMode.ON_DEMAND) {
            cancelActiveFade();
            disposeOnDemandPlayer();
        } else if (mode == PlaybackMode.MOOD) {
            // Saliendo del modo MOOD: cancelar fade, pausar el reproductor activo,
            // desconectar el listener de espectro
            cancelActiveFade();
            if (currentTrack != null && players.containsKey(currentTrack)) {
                MediaPlayer moodPlayer = players.get(currentTrack);
                if (moodPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    moodPlayer.pause();
                }
                moodPlayer.setAudioSpectrumListener(null);
            }
        } else {
            // Saliendo del modo PLAYLIST: cancelar fade, detener y liberar el reproductor
            cancelActiveFade();
            if (playlistPlayer != null) {
                playlistPlayer.stop();
                playlistPlayer.setAudioSpectrumListener(null); // T-16
                playlistPlayer.dispose();
                playlistPlayer = null;
            }
            playlistFile = null;
            playlistLoads.invalidate();
        }

        mode = target;

        // --- Setup del modo entrante ---
        if (mode == PlaybackMode.MOOD) {
            // Entrando al modo MOOD: reconectar el listener de espectro al reproductor activo
            // T-16: solo si el reproductor existe
            if (currentTrack != null && players.containsKey(currentTrack)) {
                attachSpectrumListener(players.get(currentTrack));
            }
            // No se auto-reproduce; el controlador decide
        }
        notifyPlaybackChanged();
        // Entrando al modo PLAYLIST: playlistPlayer ya es null; el controlador
        // llamará a playPlaylistTrack(...) cuando el usuario pulse play
    }

    public PlaybackMode getPlaybackMode() {
        return mode;
    }

    // =========================================================================
    // loadTrack (existente, con guard de modo — T-08)
    // =========================================================================

    /**
     * Carga un archivo de audio en un slot de mood de forma asíncrona.
     * La conversión FLAC y el cálculo de RMS ocurren en el hilo 'audio-prep'.
     * La construcción del MediaPlayer y toda mutación de JavaFX ocurren en el
     * hilo FX vía Platform.runLater.
     */
    public void loadTrack(TrackType type, File file) {
        loadTrack(type, file, null);
    }

    public void loadTrack(TrackType type, File file, Runnable onLoaded) {
        loadTrack(type, file, onLoaded, null);
    }

    public void loadTrack(TrackType type, File file, Runnable onLoaded, Runnable onFailed) {
        if (file == null || !file.exists()) return;

        final int seq = loadSeq.merge(type, 1, Integer::sum);
        final int pendingTicket = type == currentTrack
                ? beginPending(NowPlayingSource.MOOD, type, file, null) : PendingLoads.NO_TICKET;

        tags.request(file);
        audio.prep().prepare(file).whenComplete((prepared, error) -> Platform.runLater(() -> {
            if (loadSeq.getOrDefault(type, 0) != seq) {
                finishPending(pendingTicket);
                return;
            }
            if (error != null) {
                System.err.println("No se pudo cargar la pista " + type + ": " + error.getMessage());
                failPending(pendingTicket, error);
                if (onFailed != null) onFailed.run();
                return;
            }
            openMoodPlayer(type, file, prepared, seq, pendingTicket, onLoaded, onFailed);
        }));
    }

    private void openMoodPlayer(TrackType type, File file, PreparedAudio prepared, int seq, int pendingTicket,
                                Runnable onLoaded, Runnable onFailed) {
        final double gain = prepared.gain();
        final MediaPlayer player;
        try {
            player = new MediaPlayer(new Media(prepared.playable().toURI().toString()));
        } catch (RuntimeException ex) {
            System.err.println("No se pudo abrir la pista " + type + ": " + ex.getMessage());
            failPending(pendingTicket, ex);
            if (onFailed != null) onFailed.run();
            return;
        }

        player.setVolume(effectiveGain(gain));
        observeStatus(player);

        player.setOnError(() -> {
            System.err.println("Error en pista " + type + ": " + player.getError());
            Platform.runLater(() -> {
                if (players.get(type) != player && loadSeq.getOrDefault(type, 0) == seq) {
                    player.dispose();
                    failPending(pendingTicket, player.getError());
                    if (onFailed != null) onFailed.run();
                }
            });
        });

        player.setOnEndOfMedia(() -> {
            if (mode != PlaybackMode.MOOD) return;
            if (loopEnabled) {
                player.seek(Duration.ZERO);
                player.play();
            } else {
                player.pause();
                player.seek(Duration.ZERO);
            }
        });

        player.currentTimeProperty().addListener((obs, oldVal, newVal) -> emitProgress(player));

        player.setOnReady(() -> {
            if (loadSeq.getOrDefault(type, 0) != seq) {
                player.dispose();
                finishPending(pendingTicket);
                return;
            }
            boolean resumePlayback = false;
            MediaPlayer replaced = players.put(type, player);
            if (replaced != null) {
                if (type == currentTrack) {
                    cancelActiveFade();
                    resumePlayback = replaced.getStatus() == MediaPlayer.Status.PLAYING;
                }
                replaced.setAudioSpectrumListener(null);
                replaced.stop();
                replaced.dispose();
            }
            trackFiles.put(type, file);
            trackGains.put(type, gain);
            attachSpectrumListener(player);
            if (currentTrack == null) {
                currentTrack = type;
            }
            if (type == currentTrack && replaced != null && resumePlayback) player.play();
            if (onLoaded != null) onLoaded.run();
            finishPending(pendingTicket);
            notifyPlaybackChanged();
        });
    }

    public void unloadTrack(TrackType type) {
        if (type == null) return;
        loadSeq.merge(type, 1, Integer::sum);
        MediaPlayer removed = players.remove(type);
        trackFiles.remove(type);
        trackGains.remove(type);
        if (removed == null) return;
        if (type == currentTrack) {
            cancelActiveFade();
            currentTrack = null;
        }
        removed.setAudioSpectrumListener(null);
        removed.stop();
        removed.dispose();
        notifyPlaybackChanged();
    }

    public TrackType getCurrentTrack() {
        return currentTrack;
    }

    public void selectTrack(TrackType type) {
        if (mode != PlaybackMode.MOOD) return;
        if (type == null || type == currentTrack || !players.containsKey(type)) return;
        cancelActiveFade();
        if (currentTrack != null && players.containsKey(currentTrack)) {
            MediaPlayer old = players.get(currentTrack);
            old.pause();
            old.setAudioSpectrumListener(null);
        }
        currentTrack = type;
        attachSpectrumListener(players.get(type));
        notifyPlaybackChanged();
    }

    // =========================================================================
    // switchTrack (existente, con guard de modo — T-08)
    // =========================================================================

    /**
     * Cambia de mood manteniendo el tiempo de reproducción actual.
     * No-op mientras el modo activo sea PLAYLIST (T-08).
     */
    public void switchTrack(TrackType newTrack) {
        if (mode != PlaybackMode.MOOD) return; // guard T-08
        if (!players.containsKey(newTrack)) return;

        audioFocus.claimForMode();
        cancelActiveFade();

        if (previewPlayer != null) {
            previewPlayer.stop();
        }

        Duration currentTime = Duration.ZERO;
        if (currentTrack != null) {
            MediaPlayer oldPlayer = players.get(currentTrack);
            currentTime = oldPlayer.getCurrentTime();
            oldPlayer.pause();
            oldPlayer.setAudioSpectrumListener(null);
        }

        currentTrack = newTrack;
        MediaPlayer nextPlayer = players.get(newTrack);
        attachSpectrumListener(nextPlayer);
        nextPlayer.seek(currentTime);
        nextPlayer.play();

        notifyPlaybackChanged();
    }

    // =========================================================================
    // previewTrack (existente, con pausa del playlist player — T-08)
    // =========================================================================

    /**
     * Inicia la preescucha de un archivo sin afectar los slots de mood asignados.
     * Pausa el reproductor de mood activo y, si hay uno de playlist sonando, también lo pausa.
     */
    public boolean previewTrack(File file) {
        if (file == null || !file.exists()) return false;

        final PreviewTicket ticket = audioFocus.claimForNewPreview();
        previewFile = file;

        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.dispose();
            previewPlayer = null;
        }

        final int pendingTicket = beginPending(NowPlayingSource.PREVIEW, null, file, null);
        tags.request(file);
        audio.prep().prepare(file).whenComplete((prepared, error) -> Platform.runLater(
                () -> openPreviewPlayer(file, prepared, error, ticket, pendingTicket)));
        return true;
    }

    private void openPreviewPlayer(File file, PreparedAudio prepared, Throwable error, PreviewTicket ticket,
                                   int pendingTicket) {
        if (audioFocus.readinessOf(ticket) == PreviewReadiness.DISCARD) {
            finishPending(pendingTicket);
            return;
        }
        if (error != null) {
            System.err.println("Error en preparación de preescucha de " + file.getName() + ": " + error.getMessage());
            failPending(pendingTicket, error);
            return;
        }
        try {
            Media media = new Media(prepared.playable().toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            previewGain = prepared.gain();
            player.setVolume(effectiveGain(previewGain));

            player.setOnEndOfMedia(() -> Platform.runLater(() -> {
                if (player != previewPlayer) return;
                if (previewLoopEnabled) {
                    player.seek(Duration.ZERO);
                    player.play();
                } else {
                    stopPreview();
                }
            }));

            player.setOnError(() -> Platform.runLater(() -> {
                System.err.println("Preview playback error: " + player.getError());
                if (player == previewPlayer) stopPreview();
                failPending(pendingTicket, player.getError());
            }));

            player.currentTimeProperty().addListener((obs, oldVal, newVal) -> emitProgress(player));

            previewPlayer = player;
            observeStatus(player);
            attachSpectrumListener(player);
            notifyPlaybackChanged();

            player.setOnReady(() -> {
                boolean current = player == previewPlayer;
                if (current && audioFocus.readinessOf(ticket) == PreviewReadiness.PLAY) player.play();
                finishPending(pendingTicket);
            });
        } catch (RuntimeException e) {
            System.err.println("Error al iniciar preescucha de " + file.getName() + ": " + e.getMessage());
            failPending(pendingTicket, e);
        }
    }

    public void stopPreview() {
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.dispose();
            previewPlayer = null;
            notifyPlaybackChanged();
        }
    }

    // =========================================================================
    // Controles del reproductor de mood (con guards de modo — T-08)
    // =========================================================================

    public void play() {
        if (mode != PlaybackMode.MOOD) return; // guard T-08
        audioFocus.claimForMode();
        if (currentTrack != null && players.containsKey(currentTrack))
            players.get(currentTrack).play();
    }

    public void pause() {
        if (mode != PlaybackMode.MOOD) return; // guard T-08
        if (currentTrack != null && players.containsKey(currentTrack))
            players.get(currentTrack).pause();
    }

    /**
     * Baja el volumen a 0 en 1 segundo, luego pausa y restaura el volumen.
     * No-op mientras el modo activo sea PLAYLIST (T-08).
     */
    public void fadeOutAndPause(Runnable onComplete) {
        if (mode != PlaybackMode.MOOD) return; // guard T-08
        cancelActiveFade();
        if (currentTrack == null || !players.containsKey(currentTrack)) return;
        MediaPlayer player = players.get(currentTrack);
        double savedVolume = player.getVolume();
        fadingOutPlayer = player;
        activeFade = new Timeline(
            new KeyFrame(
                javafx.util.Duration.millis(1000),
                new KeyValue(player.volumeProperty(), 0.0, Interpolator.EASE_IN)
            )
        );
        activeFade.setOnFinished(e -> {
            player.pause();
            player.setVolume(savedVolume);
            activeFade = null;
            if (onComplete != null) onComplete.run();
        });
        activeFade.play();
        notifyPlaybackChanged();
    }

    /**
     * Inicia la reproducción desde volumen 0 y sube al gain original en 500ms.
     * No-op mientras el modo activo sea PLAYLIST (T-08).
     */
    public void fadeInAndPlay() {
        if (mode != PlaybackMode.MOOD) return; // guard T-08
        audioFocus.claimForMode();
        cancelActiveFade();
        if (currentTrack == null || !players.containsKey(currentTrack)) return;
        MediaPlayer player = players.get(currentTrack);
        double targetVolume = effectiveGain(trackGains.getOrDefault(currentTrack, 1.0));
        player.setVolume(0.0);
        player.play();
        activeFade = new Timeline(
            new KeyFrame(
                javafx.util.Duration.millis(500),
                new KeyValue(player.volumeProperty(), targetVolume, Interpolator.EASE_OUT)
            )
        );
        activeFade.setOnFinished(e -> activeFade = null);
        activeFade.play();
    }

    /**
     * Cancela el fade activo y restaura el volumen original del reproductor
     * que corresponda al modo actual.
     */
    private void cancelActiveFade() {
        if (activeFade != null) {
            activeFade.stop();
            fadingOutPlayer = null;
            if (mode == PlaybackMode.PLAYLIST) {
                if (playlistPlayer != null) {
                    playlistPlayer.setVolume(effectiveGain(playlistGain));
                }
            } else {
                if (currentTrack != null && players.containsKey(currentTrack)) {
                    double gain = trackGains.getOrDefault(currentTrack, 1.0);
                    players.get(currentTrack).setVolume(effectiveGain(gain));
                }
            }
            activeFade = null;
            notifyPlaybackChanged();
        }
    }

    public boolean isPlaying() {
        if (currentTrack == null || !players.containsKey(currentTrack)) return false;
        return isSounding(players.get(currentTrack));
    }

    // =========================================================================
    // Controles del reproductor de preescucha
    // =========================================================================

    public void previewPlay() {
        if (previewPlayer == null) return;
        audioFocus.claimForPreview();
        previewPlayer.play();
    }

    public boolean previewSoundedLast() {
        return audioFocus.previewSoundedLast();
    }

    private void pausePreviewPlayer() {
        previewPlayer.pause();
    }

    private void pauseModePlayers() {
        cancelActiveFade();
        if (currentTrack != null && players.containsKey(currentTrack)) {
            players.get(currentTrack).pause();
        }
        if (playlistPlayer != null) playlistPlayer.pause();
        if (onDemandPlayer != null) onDemandPlayer.pause();
    }

    public void previewPause() {
        if (previewPlayer != null) previewPlayer.pause();
    }

    public boolean isPreviewPlaying() {
        return previewPlayer != null && previewPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }

    public boolean isPreviewLoaded() {
        return previewPlayer != null;
    }

    public void setPreviewLoop(boolean enabled) {
        this.previewLoopEnabled = enabled;
    }

    public void seekPreview(double fraction) {
        if (previewPlayer != null) {
            Duration total = previewPlayer.getTotalDuration();
            if (total != null && !total.isUnknown())
                previewPlayer.seek(total.multiply(fraction));
        }
    }

    // =========================================================================
    // T-09: playPlaylistTrack + métodos de transporte del playlist
    // =========================================================================

    /**
     * Carga y reproduce un archivo de audio en el modo playlist.
     * No-op si el modo activo no es PLAYLIST.
     * Reutiliza el pipeline asíncrono de loadTrack:
     */
    public void playPlaylistTrack(File file) {
        if (mode != PlaybackMode.PLAYLIST) return;
        audioFocus.claimForMode();
        cancelActiveFade();
        final int seq = playlistLoads.begin();
        playlistPendingTicket = beginPending(NowPlayingSource.PLAYLIST, null, file, null);

        tags.request(file);
        playlistPrep = audio.prep().prepare(file);
        playlistPrep.whenComplete((prepared, error) -> Platform.runLater(() -> {
            if (error != null) {
                System.err.println("No se pudo cargar la pista de playlist: " + error.getMessage());
                failPlaylistLoad(seq, error);
                return;
            }
            openPlaylistPlayer(file, prepared.playable(), prepared.gain(), seq);
        }));
    }

    public void prefetch(File next) {
        audio.prefetcher().schedule(next, playlistPrep);
    }

    public AudioServices audio() {
        return audio;
    }

    private void openPlaylistPlayer(File file, File playable, double gain, int seq) {
        if (!playlistLoads.isCurrent(seq) || mode != PlaybackMode.PLAYLIST) {
            if (playlistLoads.isCurrent(seq)) finishPending(playlistPendingTicket);
            return;
        }
        final MediaPlayer player;
        try {
            player = new MediaPlayer(new Media(playable.toURI().toString()));
        } catch (RuntimeException ex) {
            System.err.println("Could not open playlist track: " + ex.getMessage());
            failPlaylistLoad(seq, ex);
            return;
        }
        player.setVolume(effectiveGain(gain));
        observeStatus(player);
        player.currentTimeProperty().addListener((obs, oldVal, newVal) -> emitProgress(player));
        player.setOnEndOfMedia(() -> Platform.runLater(() -> {
            if (mode == PlaybackMode.PLAYLIST && player == playlistPlayer && onPlaylistEnd != null) {
                onPlaylistEnd.run();
            }
        }));
        player.setOnError(() -> Platform.runLater(() -> handlePlaylistPlayerError(player, seq)));
        player.setOnReady(() -> commitPlaylistPlayer(player, file, gain, seq));
    }

    private void commitPlaylistPlayer(MediaPlayer player, File file, double gain, int seq) {
        if (!playlistLoads.isCurrent(seq) || mode != PlaybackMode.PLAYLIST) {
            player.dispose();
            if (playlistLoads.isCurrent(seq)) finishPending(playlistPendingTicket);
            return;
        }
        consecutivePlaylistFailures = 0;
        cancelActiveFade();
        releasePlaylistPlayer();
        playlistGain = gain;
        playlistPlayer = player;
        playlistFile = file;
        attachSpectrumListener(player);
        player.play();
        finishPending(playlistPendingTicket);
        notifyPlaybackChanged();
    }

    private void handlePlaylistPlayerError(MediaPlayer player, int seq) {
        System.err.println("Playlist playback error: " + player.getError());
        if (player == playlistPlayer) {
            if (mode == PlaybackMode.PLAYLIST && onPlaylistTrackFailed != null) onPlaylistTrackFailed.run();
            return;
        }
        player.dispose();
        failPlaylistLoad(seq, player.getError());
    }

    private void failPlaylistLoad(int seq, Throwable error) {
        if (!playlistLoads.isCurrent(seq)) return;
        failPending(playlistPendingTicket, error);
        boolean canSkip = onPlaylistTrackFailed != null
                && ++consecutivePlaylistFailures <= MAX_CONSECUTIVE_PLAYLIST_FAILURES;
        if (canSkip) {
            onPlaylistTrackFailed.run();
            return;
        }
        consecutivePlaylistFailures = 0;
        releasePlaylistState();
    }

    private void releasePlaylistPlayer() {
        if (playlistPlayer == null) return;
        playlistPlayer.setAudioSpectrumListener(null);
        playlistPlayer.stop();
        playlistPlayer.dispose();
        playlistPlayer = null;
    }

    /**
     * Reanuda la reproducción del playlist si está pausado.
     * No-op si el modo activo no es PLAYLIST.
     */
    public void playlistPlay() {
        if (mode != PlaybackMode.PLAYLIST) return;
        audioFocus.claimForMode();
        cancelActiveFade();
        if (playlistPlayer != null
                && playlistPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
            playlistPlayer.play();
        }
    }

    /**
     * Pausa el reproductor de playlist con fade-out de 1 segundo.
     * No-op si el modo activo no es PLAYLIST.
     */
    public void playlistPause() {
        if (mode != PlaybackMode.PLAYLIST) return;
        if (playlistPlayer == null) return;
        if (playlistPlayer.getStatus() != MediaPlayer.Status.PLAYING) return;

        cancelActiveFade();
        final double savedGain = playlistGain;
        final MediaPlayer fading = playlistPlayer;
        fadingOutPlayer = fading;
        activeFade = new Timeline(
            new KeyFrame(
                javafx.util.Duration.millis(1000),
                new KeyValue(fading.volumeProperty(), 0.0, Interpolator.EASE_IN)
            )
        );
        activeFade.setOnFinished(e -> {
            fading.pause();
            fading.setVolume(effectiveGain(savedGain));
            activeFade = null;
        });
        activeFade.play();
        notifyPlaybackChanged();
    }

    /**
     * Detiene y libera el reproductor de playlist.
     * T-17: null-guard antes de dispose; null del campo después de dispose.
     */
    public void stopPlaylist() {
        finishPending(playlistPendingTicket);
        releasePlaylistState();
    }

    private void releasePlaylistState() {
        playlistLoads.invalidate();
        if (mode == PlaybackMode.PLAYLIST) cancelActiveFade();
        releasePlaylistPlayer();
        playlistFile = null;
        notifyPlaybackChanged();
    }

    /**
     * Busca la posición en la pista actual del playlist.
     * No-op si el modo activo no es PLAYLIST.
     */
    public void seekPlaylist(double fraction) {
        if (mode != PlaybackMode.PLAYLIST) return;
        if (playlistPlayer != null) {
            Duration total = playlistPlayer.getTotalDuration();
            if (total != null && !total.isUnknown() && total.toMillis() > 0) {
                playlistPlayer.seek(total.multiply(fraction));
            }
        }
    }

    /** Retorna true si el reproductor de playlist está en estado PLAYING. */
    public boolean isPlaylistPlaying() {
        return isSounding(playlistPlayer);
    }

    /** Establece el callback que se invoca al llegar al fin de una pista del playlist. */
    public void setOnPlaylistEnd(Runnable r) {
        this.onPlaylistEnd = r;
    }

    public void setOnPlaylistTrackFailed(Runnable r) {
        this.onPlaylistTrackFailed = r;
    }

    private void disposeOnDemandPlayer() {
        if (onDemandPlayer != null) {
            onDemandPlayer.setAudioSpectrumListener(null);
            onDemandPlayer.stop();
            onDemandPlayer.dispose();
            onDemandPlayer = null;
        }
        onDemandFile = null;
        onDemandTitle = null;
        notifyPlaybackChanged();
    }

    public void playOnDemand(File file, String title, Runnable onStarted) {
        if (file == null || !file.exists()) return;
        audioFocus.claimForMode();
        final int seq = ++onDemandSeq;
        final int pendingTicket = beginPending(NowPlayingSource.ON_DEMAND, null, file, title);

        tags.request(file);
        audio.prep().prepare(file).whenComplete((prepared, error) -> Platform.runLater(
                () -> openOnDemandPlayer(file, title, onStarted, prepared, error, seq, pendingTicket)));
    }

    private void openOnDemandPlayer(File file, String title, Runnable onStarted, PreparedAudio prepared,
                                    Throwable error, int seq, int pendingTicket) {
        if (seq != onDemandSeq) {
            finishPending(pendingTicket);
            return;
        }
        if (error != null) {
            System.err.println("Could not load on-demand track: " + error.getMessage());
            failPending(pendingTicket, error);
            return;
        }
        final double gain = prepared.gain();
        final MediaPlayer player;
        try {
            player = new MediaPlayer(new Media(prepared.playable().toURI().toString()));
        } catch (RuntimeException ex) {
            System.err.println("Could not open on-demand track: " + ex.getMessage());
            failPending(pendingTicket, ex);
            return;
        }

        observeStatus(player);
        player.currentTimeProperty().addListener((obs, o, nv) -> emitProgress(player));
        player.setOnEndOfMedia(() -> Platform.runLater(() -> {
            if (player != onDemandPlayer) return;
            if (onDemandLoop) {
                player.seek(Duration.ZERO);
                player.play();
            } else {
                player.pause();
                player.seek(Duration.ZERO);
            }
        }));
        player.setOnError(() -> {
            System.err.println("On-demand playback error: " + player.getError());
            Platform.runLater(() -> {
                if (player != onDemandPlayer) player.dispose();
                failPending(pendingTicket, player.getError());
                notifyPlaybackChanged();
            });
        });
        player.setOnReady(() -> {
            if (seq != onDemandSeq) {
                player.dispose();
                finishPending(pendingTicket);
                return;
            }
            setPlaybackMode(PlaybackMode.ON_DEMAND);
            disposeOnDemandPlayer();
            stopPreview();
            onDemandGain = gain;
            player.setVolume(effectiveGain(gain));
            attachSpectrumListener(player);
            onDemandPlayer = player;
            onDemandFile = file;
            onDemandTitle = title != null && !title.isBlank() ? title : null;
            player.play();
            if (onStarted != null) onStarted.run();
            finishPending(pendingTicket);
        });
    }

    public void onDemandPlay() {
        if (mode != PlaybackMode.ON_DEMAND || onDemandPlayer == null) return;
        audioFocus.claimForMode();
        onDemandPlayer.play();
    }

    public void onDemandPause() {
        if (mode == PlaybackMode.ON_DEMAND && onDemandPlayer != null) onDemandPlayer.pause();
    }

    public boolean isOnDemandPlaying() {
        return mode == PlaybackMode.ON_DEMAND && isSounding(onDemandPlayer);
    }

    public void seekOnDemand(double fraction) {
        if (onDemandPlayer == null) return;
        Duration total = onDemandPlayer.getTotalDuration();
        if (total != null && !total.isUnknown() && total.toMillis() > 0)
            onDemandPlayer.seek(total.multiply(fraction));
    }

    public void setOnDemandLoop(boolean enabled) {
        this.onDemandLoop = enabled;
    }

    // =========================================================================
    // Getters y setters estructurales
    // =========================================================================

    public boolean isLoaded(TrackType type) {
        return players.containsKey(type);
    }

    public void setLoop(boolean enabled) {
        this.loopEnabled = enabled;
    }

    public void seekCurrentTrack(double fraction) {
        if (currentTrack != null && players.containsKey(currentTrack)) {
            MediaPlayer player = players.get(currentTrack);
            Duration total = player.getTotalDuration();
            if (total != null && !total.isUnknown())
                player.seek(total.multiply(fraction));
        }
    }
}
