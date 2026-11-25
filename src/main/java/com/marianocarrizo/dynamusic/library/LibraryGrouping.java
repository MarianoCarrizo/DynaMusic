package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.service.AudioFileCollector;
import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LibraryGrouping {

    static final String UNKNOWN_ARTIST = "Unknown artist";
    static final String VARIOUS_ARTISTS = "Various artists";
    static final String UNKNOWN_ALBUM = "Unknown album";
    private static final Pattern DISC_FOLDER = Pattern.compile("(?i)(cd|disc|disk) ?\\d+");
    private static final int NO_TRACK_NUMBER = Integer.MAX_VALUE;

    private static final Comparator<TrackEntry> TRACK_ORDER = Comparator
            .comparing((TrackEntry entry) -> parentOf(entry.path()), AudioFileCollector::compareNatural)
            .thenComparingInt(entry -> entry.tags().trackNumber() == 0 ? NO_TRACK_NUMBER : entry.tags().trackNumber())
            .thenComparing(entry -> entry.path(), AudioFileCollector::compareNatural);

    private LibraryGrouping() {}

    public static LibrarySnapshot group(Collection<TrackEntry> entries) {
        Map<AlbumKey, List<TrackEntry>> tracksByAlbum = new LinkedHashMap<>();
        Map<AlbumKey, String> titles = new LinkedHashMap<>();
        List<TrackEntry> ordered = entries.stream().sorted(Comparator.comparing(TrackEntry::path)).toList();
        int counted = 0;
        for (TrackEntry entry : ordered) {
            Path folder = albumFolder(entry.path());
            if (folder == null) continue;
            String title = albumTitle(entry.tags(), folder);
            AlbumKey key = new AlbumKey(NameNormalizer.normalize(title), folder.toString());
            tracksByAlbum.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
            titles.putIfAbsent(key, title);
            counted++;
        }
        List<Album> albums = new ArrayList<>();
        for (Map.Entry<AlbumKey, List<TrackEntry>> album : tracksByAlbum.entrySet()) {
            albums.add(buildAlbum(album.getKey(), titles.get(album.getKey()), album.getValue()));
        }
        albums.sort(Comparator.comparing((Album album) -> album.key().title(), AudioFileCollector::compareNatural)
                .thenComparing(album -> album.key().folder()));
        return new LibrarySnapshot(List.copyOf(albums), artistsOf(albums), counted);
    }

    private static Album buildAlbum(AlbumKey key, String title, List<TrackEntry> unsorted) {
        List<TrackEntry> tracks = unsorted.stream().sorted(TRACK_ORDER).toList();
        return new Album(key, title, albumArtist(tracks), firstYear(tracks), tracks, firstCover(tracks));
    }

    private static String albumArtist(List<TrackEntry> tracks) {
        Map<String, String> named = new LinkedHashMap<>();
        for (TrackEntry track : tracks) {
            String artist = track.tags().artist();
            if (!artist.isEmpty()) named.putIfAbsent(NameNormalizer.normalize(artist), artist);
        }
        if (named.isEmpty()) return UNKNOWN_ARTIST;
        return named.size() == 1 ? named.values().iterator().next() : VARIOUS_ARTISTS;
    }

    private static String firstYear(List<TrackEntry> tracks) {
        for (TrackEntry track : tracks) {
            if (!track.tags().year().isEmpty()) return track.tags().year();
        }
        return "";
    }

    private static String firstCover(List<TrackEntry> tracks) {
        for (TrackEntry track : tracks) {
            if (track.tags().hasCover()) return track.path();
        }
        return "";
    }

    private static List<Artist> artistsOf(List<Album> albums) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, List<Album>> albumsByArtist = new LinkedHashMap<>();
        for (Album album : albums) {
            for (String artist : artistsIn(album)) {
                String normalized = NameNormalizer.normalize(artist);
                names.putIfAbsent(normalized, artist);
                albumsByArtist.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(album);
            }
        }
        List<Artist> artists = new ArrayList<>();
        for (Map.Entry<String, List<Album>> entry : albumsByArtist.entrySet()) {
            artists.add(new Artist(names.get(entry.getKey()), List.copyOf(entry.getValue())));
        }
        artists.sort(Comparator.comparing((Artist artist) -> NameNormalizer.normalize(artist.name()),
                AudioFileCollector::compareNatural));
        return List.copyOf(artists);
    }

    private static Set<String> artistsIn(Album album) {
        Set<String> artists = new LinkedHashSet<>();
        for (TrackEntry track : album.tracks()) {
            if (!track.tags().artist().isEmpty()) artists.add(track.tags().artist());
        }
        if (artists.isEmpty()) artists.add(UNKNOWN_ARTIST);
        return artists;
    }

    private static String albumTitle(TrackTags tags, Path folder) {
        if (!tags.album().isEmpty()) return tags.album();
        Path name = folder.getFileName();
        String folderName = name == null ? folder.toString() : name.toString();
        return folderName.isEmpty() ? UNKNOWN_ALBUM : folderName;
    }

    private static Path albumFolder(String path) {
        try {
            Path parent = Path.of(path).getParent();
            if (parent == null) return Path.of("");
            Path name = parent.getFileName();
            boolean discFolder = name != null && DISC_FOLDER.matcher(name.toString()).matches();
            return discFolder && parent.getParent() != null ? parent.getParent() : parent;
        } catch (InvalidPathException invalid) {
            return null;
        }
    }

    private static String parentOf(String path) {
        Path parent = Path.of(path).getParent();
        return parent == null ? "" : parent.toString();
    }
}
