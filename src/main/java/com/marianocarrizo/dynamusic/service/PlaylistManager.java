package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.model.Playlist;
import com.marianocarrizo.dynamusic.model.PlaylistItem;
import com.marianocarrizo.dynamusic.model.RepeatMode;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Gestiona el estado de las playlists y el orden de reproducción.
 * Es puro estado + lógica de índices; no contiene ningún MediaPlayer ni código de audio.
 * Todos sus métodos deben invocarse desde el hilo FX.
 */
public class PlaylistManager {

    // Lista compartida con SessionConfig en el momento de guardar
    private List<Playlist> playlists;

    // Playlist activa actualmente seleccionada; null si ninguna
    private Playlist activePlaylist;

    // Puntero dentro del buffer de orden (0..order.length-1)
    private int position;

    private boolean currentTrackRemoved;

    private boolean shuffle;
    private RepeatMode repeat = RepeatMode.OFF;

    // Buffer de reproducción: permutación de índices [0..n-1] de activePlaylist.paths
    private int[] order;

    private static final boolean CASE_INSENSITIVE_PATHS =
            YtDlpManager.isWindows(System.getProperty("os.name", ""));

    private final Random random = new Random();

    public PlaylistManager() {
        this.playlists = new ArrayList<>();
        this.order = new int[0];
        this.position = 0;
    }

    // =========================================================================
    // T-03: CRUD de playlists + selección activa
    // =========================================================================

    /**
     * Crea una playlist nueva con el nombre dado (trimmed).
     * Rechaza nombres en blanco o duplicados (case-insensitive).
     * Retorna la instancia creada, o null si fue rechazada.
     */
    public Playlist createPlaylist(String name) {
        if (name == null) return null;
        name = name.trim();
        if (name.isEmpty()) return null;
        final String finalName = name;
        boolean duplicate = playlists.stream()
                .anyMatch(p -> p.getName() != null && p.getName().equalsIgnoreCase(finalName));
        if (duplicate) return null;
        Playlist playlist = new Playlist(finalName);
        playlists.add(playlist);
        return playlist;
    }

    /**
     * Elimina la playlist con el nombre dado.
     * Si era la playlist activa, limpia el estado activo y el buffer.
     * Retorna true si fue encontrada y eliminada.
     */
    public boolean deletePlaylist(String name) {
        if (name == null) return false;
        boolean removed = playlists.removeIf(
                p -> p.getName() != null && p.getName().equalsIgnoreCase(name));
        if (removed && activePlaylist != null
                && activePlaylist.getName() != null
                && activePlaylist.getName().equalsIgnoreCase(name)) {
            activePlaylist = null;
            order = new int[0];
            position = 0;
            currentTrackRemoved = false;
        }
        return removed;
    }

    /**
     * Renombra una playlist.
     * Rechaza el nuevo nombre si está en blanco o ya existe (excluyendo la propia).
     * Retorna true si el renombrado fue exitoso.
     */
    public boolean renamePlaylist(String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        newName = newName.trim();
        if (newName.isEmpty()) return false;
        final String finalNewName = newName;
        boolean duplicate = playlists.stream()
                .anyMatch(p -> p.getName() != null
                        && !p.getName().equalsIgnoreCase(oldName)
                        && p.getName().equalsIgnoreCase(finalNewName));
        if (duplicate) return false;
        Optional<Playlist> found = findByName(oldName);
        if (found.isEmpty()) return false;
        found.get().setName(finalNewName);
        return true;
    }

    /**
     * Establece la playlist activa por nombre.
     * Si no se encuentra, deja el estado activo en null.
     * Siempre reinicia position=0 y reconstruye el buffer de orden.
     */
    public void setActivePlaylist(String name) {
        Optional<Playlist> found = findByName(name);
        if (found.isPresent() && found.get() == activePlaylist) return;
        currentTrackRemoved = false;
        if (found.isEmpty()) {
            activePlaylist = null;
            order = new int[0];
            position = 0;
            return;
        }
        activePlaylist = found.get();
        position = 0;
        rebuildOrder();
    }

    /**
     * Reemplaza la lista interna de playlists (usada al restaurar sesión).
     * No llama a setActivePlaylist; el llamador lo hace después.
     */
    public void setPlaylists(List<Playlist> loaded) {
        this.playlists = (loaded != null) ? loaded : new ArrayList<>();
    }

    /** Retorna la referencia viva a la lista de playlists (compartida con SessionConfig). */
    public List<Playlist> getPlaylists() {
        return playlists;
    }

    /** Busca una playlist por nombre (case-insensitive). */
    public Optional<Playlist> findByName(String name) {
        if (name == null) return Optional.empty();
        return playlists.stream()
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /** Retorna el nombre de la playlist activa, o null si no hay ninguna. */
    public String getActivePlaylistName() {
        return activePlaylist != null ? activePlaylist.getName() : null;
    }

    // =========================================================================
    // T-04: Edición de pistas con reconstrucción de orden
    // =========================================================================

    public void addTrack(String playlistName, File file) {
        insertTracks(playlistName, List.of(file), Integer.MAX_VALUE);
    }

    public Playlist ensurePlaylist(String name) {
        return findByName(name).orElseGet(() -> createPlaylist(name));
    }

    public int insertTracks(String playlistName, List<File> files, int index) {
        Optional<Playlist> found = findByName(playlistName);
        if (found.isEmpty()) return 0;
        Playlist playlist = found.get();
        List<String> newPaths = newPathsOnly(playlist, files);
        if (newPaths.isEmpty()) return 0;
        int oldSize = playlist.size();
        int at = Math.max(0, Math.min(index, oldSize));
        List<PlaylistItem> items = playlist.getItems();
        for (int i = 0; i < newPaths.size(); i++) items.add(at + i, new PlaylistItem(newPaths.get(i)));
        if (isActivePlaylist(playlistName)) {
            remapOrder(insertionMapping(oldSize, at, newPaths.size()), playlist.size());
        }
        return newPaths.size();
    }

    private List<String> newPathsOnly(Playlist playlist, List<File> files) {
        Set<String> known = new HashSet<>();
        for (String path : playlist.getPaths()) known.add(pathKey(path));
        List<String> fresh = new ArrayList<>();
        for (File file : files) {
            String path = file.getAbsolutePath();
            if (known.add(pathKey(path))) fresh.add(path);
        }
        return fresh;
    }

    private static String pathKey(String path) {
        String normalized;
        try {
            normalized = Path.of(path).normalize().toString();
        } catch (InvalidPathException e) {
            normalized = path;
        }
        return CASE_INSENSITIVE_PATHS ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    public void addItem(String playlistName, PlaylistItem item) {
        findByName(playlistName).ifPresent(pl -> {
            pl.addItem(item);
            if (isActivePlaylist(playlistName)) rebuildOrder();
        });
    }

    public void removeTrack(String playlistName, int index) {
        Optional<Playlist> found = findByName(playlistName);
        if (found.isEmpty()) return;
        Playlist playlist = found.get();
        int oldSize = playlist.size();
        if (index < 0 || index >= oldSize) return;
        playlist.removeAt(index);
        if (isActivePlaylist(playlistName)) {
            remapOrder(removalMapping(oldSize, index), playlist.size());
        }
    }

    public void reorderTrack(String playlistName, int from, int to) {
        Optional<Playlist> found = findByName(playlistName);
        if (found.isEmpty()) return;
        Playlist playlist = found.get();
        int size = playlist.size();
        if (from < 0 || from >= size || to < 0 || to >= size || from == to) return;
        playlist.reorder(from, to);
        if (isActivePlaylist(playlistName)) {
            remapOrder(moveMapping(size, from, to), size);
        }
    }

    private static int[] insertionMapping(int oldSize, int at, int count) {
        int[] mapping = new int[oldSize];
        for (int i = 0; i < oldSize; i++) mapping[i] = i < at ? i : i + count;
        return mapping;
    }

    private static int[] removalMapping(int oldSize, int removed) {
        int[] mapping = new int[oldSize];
        for (int i = 0; i < oldSize; i++) mapping[i] = i < removed ? i : i - 1;
        mapping[removed] = -1;
        return mapping;
    }

    private static int[] moveMapping(int size, int from, int to) {
        int[] mapping = new int[size];
        for (int i = 0; i < size; i++) {
            if (i == from) mapping[i] = to;
            else if (from < to && i > from && i <= to) mapping[i] = i - 1;
            else if (from > to && i >= to && i < from) mapping[i] = i + 1;
            else mapping[i] = i;
        }
        return mapping;
    }

    private void remapOrder(int[] oldToNew, int newSize) {
        if (order.length == 0) {
            rebuildOrder();
            return;
        }
        int anchorOld = position < order.length ? order[position] : -1;
        int keptBefore = 0;
        Set<Integer> sequence = new LinkedHashSet<>();
        for (int i = 0; i < order.length; i++) {
            int mapped = oldToNew[order[i]];
            if (mapped < 0) continue;
            if (i < position) keptBefore++;
            if (shuffle) sequence.add(mapped);
        }
        for (int i = 0; i < newSize; i++) sequence.add(i);
        order = sequence.stream().mapToInt(Integer::intValue).toArray();
        int anchorNew = anchorOld >= 0 ? oldToNew[anchorOld] : -1;
        if (anchorNew >= 0) {
            position = indexInOrder(anchorNew);
        } else {
            if (anchorOld >= 0) currentTrackRemoved = true;
            position = keptBefore;
        }
        if (order.length == 0) currentTrackRemoved = false;
        clampPosition();
    }

    private int indexInOrder(int naturalIndex) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == naturalIndex) return i;
        }
        return 0;
    }

    /** Ajusta position para que nunca quede fuera del rango del buffer. */
    private void clampPosition() {
        if (order.length == 0) {
            position = 0;
        } else {
            int last = currentTrackRemoved ? order.length : order.length - 1;
            position = Math.max(0, Math.min(position, last));
        }
    }

    /**
     * Reconstruye el buffer de orden según el estado de shuffle.
     * Shuffle OFF → orden identidad; Shuffle ON → Fisher-Yates.
     */
    private void rebuildOrder() {
        if (activePlaylist == null || activePlaylist.size() == 0) {
            order = new int[0];
            position = 0;
            return;
        }
        if (shuffle) {
            buildShuffledOrder();
        } else {
            int n = activePlaylist.size();
            order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
        }
        clampPosition();
    }

    private boolean isActivePlaylist(String name) {
        return activePlaylist != null
                && activePlaylist.getName() != null
                && activePlaylist.getName().equalsIgnoreCase(name);
    }

    // =========================================================================
    // T-05: Buffer de shuffle + navegación
    // =========================================================================

    /**
     * Construye una permutación Fisher-Yates del buffer, anclando la pista actual
     * (índice natural) en position=0 para que el track actual no cambie al activar shuffle.
     */
    private void buildShuffledOrder() {
        int n = activePlaylist.size();
        if (n == 0) {
            order = new int[0];
            position = 0;
            return;
        }
        // Índice natural de la pista actualmente en reproducción
        int currentNaturalIndex = (order.length > 0 && position < order.length)
                ? order[position] : 0;

        int[] newOrder = new int[n];
        for (int i = 0; i < n; i++) newOrder[i] = i;

        // Fisher-Yates
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = newOrder[i];
            newOrder[i] = newOrder[j];
            newOrder[j] = tmp;
        }

        // Intercambiar el elemento que vale currentNaturalIndex a la posición 0
        // para que la pista actual siga siendo la primera al activar shuffle mid-play
        for (int i = 0; i < n; i++) {
            if (newOrder[i] == currentNaturalIndex) {
                newOrder[i] = newOrder[0];
                newOrder[0] = currentNaturalIndex;
                break;
            }
        }

        order = newOrder;
        position = 0;
    }

    /**
     * Retorna la pista en la posición actual del buffer, o vacío si no hay playlist activa.
     */
    public Optional<File> getCurrentTrack() {
        if (activePlaylist == null || order.length == 0) return Optional.empty();
        List<String> paths = activePlaylist.getPaths();
        if (paths.isEmpty()) return Optional.empty();
        if (position >= order.length) return Optional.empty();
        int idx = order[position];
        if (idx < 0 || idx >= paths.size()) return Optional.empty();
        return Optional.of(new File(paths.get(idx)));
    }

    /**
     * Avanza al siguiente track en el buffer de orden.
     * Comportamiento al llegar al final:
     *  - Loop ON  → reinicia (y baraja de nuevo si shuffle está activo); retorna el primer track.
     *  - Loop OFF → retorna Optional.empty() (señal de STOP para el llamador).
     */
    public Optional<File> next() {
        if (activePlaylist == null || order.length == 0) return Optional.empty();
        if (currentTrackRemoved) {
            currentTrackRemoved = false;
            if (position < order.length) return getCurrentTrack();
            position = order.length - 1;
        }
        if (position < order.length - 1) {
            position++;
            return getCurrentTrack();
        }
        // Fin de la playlist
        if (repeat == RepeatMode.ALL) {
            if (shuffle) {
                buildShuffledOrder(); // reinicia barajado; position ya queda en 0
            } else {
                int n = activePlaylist.size();
                order = new int[n];
                for (int i = 0; i < n; i++) order[i] = i;
                position = 0;
            }
            return getCurrentTrack();
        }
        return Optional.empty(); // señal de STOP
    }

    /**
     * Retrocede al track anterior en el buffer de orden.
     * En position=0 es un no-op (no hace wrap); retorna el track actual.
     */
    public Optional<File> prev() {
        if (currentTrackRemoved) {
            currentTrackRemoved = false;
            position = Math.min(position, order.length);
        }
        if (position > 0) {
            position--;
        }
        return getCurrentTrack();
    }

    /**
     * Salta directamente a la pista cuyo índice natural en la playlist es {@code uiIndex}.
     * Busca linealmente en el buffer; si no se encuentra, clampea el índice.
     */
    public Optional<File> startAt(int uiIndex) {
        currentTrackRemoved = false;
        if (activePlaylist == null || order.length == 0) return Optional.empty();
        for (int i = 0; i < order.length; i++) {
            if (order[i] == uiIndex) {
                position = i;
                return getCurrentTrack();
            }
        }
        position = Math.max(0, Math.min(uiIndex, order.length - 1));
        return getCurrentTrack();
    }

    /**
     * Activa o desactiva el modo shuffle.
     * Al activar: construye permutación Fisher-Yates anclando la pista actual.
     * Al desactivar: restaura orden identidad con position igual al índice natural actual.
     */
    public void setShuffle(boolean on) {
        this.shuffle = on;
        currentTrackRemoved = false;
        clampPosition();
        if (on) {
            buildShuffledOrder();
        } else {
            if (activePlaylist != null && activePlaylist.size() > 0) {
                int currentNaturalIdx = (order.length > 0 && position < order.length)
                        ? order[position] : 0;
                int n = activePlaylist.size();
                order = new int[n];
                for (int i = 0; i < n; i++) order[i] = i;
                position = Math.max(0, Math.min(currentNaturalIdx, n - 1));
            }
        }
    }

    public void setRepeat(RepeatMode mode) {
        this.repeat = mode;
    }

    public RepeatMode getRepeat() { return repeat; }

    public Optional<File> nextAfterEnd() {
        if (repeat == RepeatMode.ONE && !currentTrackRemoved) return getCurrentTrack();
        return next();
    }

    public Optional<File> peekNext() {
        if (activePlaylist == null || order.length == 0) return Optional.empty();
        if (repeat == RepeatMode.ONE && !currentTrackRemoved) return getCurrentTrack();
        int cursor = position;
        if (currentTrackRemoved) {
            if (cursor < order.length) return fileAtNaturalIndex(order[cursor]);
            cursor = order.length - 1;
        }
        if (cursor < order.length - 1) return fileAtNaturalIndex(order[cursor + 1]);
        if (repeat == RepeatMode.ALL && !shuffle) return fileAtNaturalIndex(0);
        return Optional.empty();
    }

    private Optional<File> fileAtNaturalIndex(int index) {
        List<String> paths = activePlaylist.getPaths();
        if (index < 0 || index >= paths.size()) return Optional.empty();
        return Optional.of(new File(paths.get(index)));
    }

    public boolean isShuffle() { return shuffle; }

    /** Cantidad de pistas de la playlist activa; 0 si no hay ninguna seleccionada. */
    public int size() {
        return activePlaylist == null ? 0 : activePlaylist.size();
    }

    /**
     * Retorna la lista de rutas absolutas de la playlist activa,
     * o una lista vacía si no hay ninguna seleccionada.
     */
    public List<String> getActivePaths() {
        if (activePlaylist == null) return new ArrayList<>();
        return activePlaylist.getPaths();
    }

    public List<PlaylistItem> getActiveItems() {
        if (activePlaylist == null) return new ArrayList<>();
        return activePlaylist.getItems();
    }

    /**
     * Índice natural (en la playlist) de la pista actualmente en reproducción.
     * Usado por el ListView para resaltar la fila correcta.
     */
    public int currentDisplayIndex() {
        if (order.length == 0) return 0;
        if (currentTrackRemoved) return -1;
        return order[position];
    }

    public Optional<File> startCurrent() {
        currentTrackRemoved = false;
        return getCurrentTrack();
    }

    /**
     * Etiqueta de posición en formato "N / T" (base 1), por ejemplo "2 / 5".
     * Retorna "— / —" si no hay playlist activa o está vacía.
     */
    public String positionLabel() {
        if (size() == 0) return "— / —";
        if (currentTrackRemoved) return "— / " + size();
        return (currentDisplayIndex() + 1) + " / " + size();
    }
}
