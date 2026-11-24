package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexCacheCodecTest {

    private static TrackEntry entry(String path) {
        return new TrackEntry(path, 1234, 99, new TrackTags("Title", "Artist", "Album", 7, "2001", true));
    }

    private static String line(String... fields) {
        return String.join("\t", fields);
    }

    private static String file(String... lines) {
        return IndexCacheCodec.HEADER + "\n" + String.join("\n", lines) + "\n";
    }

    @Test
    void roundTripKeepsEveryField() {
        List<TrackEntry> entries = List.of(entry("/m/a.flac"), new TrackEntry("/m/b.mp3", 0, 0, TrackTags.EMPTY));

        List<TrackEntry> decoded = IndexCacheCodec.decode(IndexCacheCodec.encode(entries));

        assertEquals(entries, decoded);
    }

    @Test
    void textWithAccentsAndSymbolsSurvives() {
        TrackEntry accented = new TrackEntry("/m/á.flac", 1, 1,
                new TrackTags("Café ♪", "Björk", "Æon", 1, "", false));

        assertEquals(List.of(accented), IndexCacheCodec.decode(IndexCacheCodec.encode(List.of(accented))));
    }

    @Test
    void pathsWithSeparatorsAreSkippedWhenEncoding() {
        List<TrackEntry> decoded = IndexCacheCodec.decode(
                IndexCacheCodec.encode(List.of(entry("/m/bad\tname.flac"), entry("/m/bad\nname.flac"), entry("/m/ok.flac"))));

        assertEquals(List.of(entry("/m/ok.flac")), decoded);
    }

    @Test
    void wrongHeaderDecodesToNothing() {
        String text = "dynamusic-library-index 0\n" + line("/m/a.flac", "1", "1", "1", "0", "", "t", "a", "b") + "\n";

        assertTrue(IndexCacheCodec.decode(text).isEmpty());
        assertTrue(!IndexCacheCodec.hasValidHeader(text));
    }

    @Test
    void emptyAndNullTextDecodeToNothing() {
        assertTrue(IndexCacheCodec.decode("").isEmpty());
        assertTrue(IndexCacheCodec.decode(null).isEmpty());
    }

    @Test
    void badLinesAreSkippedAndGoodOnesKept() {
        String text = file(
                line("/m/good.flac", "10", "20", "3", "1", "2000", "t", "a", "b"),
                "just one field",
                line("/m/short.flac", "10", "20"),
                line("/m/size.flac", "ten", "20", "3", "0", "", "t", "a", "b"),
                line("/m/negative.flac", "-1", "20", "3", "0", "", "t", "a", "b"),
                line("/m/flag.flac", "10", "20", "3", "2", "", "t", "a", "b"),
                line("/m/track.flac", "10", "20", "-3", "0", "", "t", "a", "b"),
                "",
                line("/m/extra.flac", "10", "20", "3", "0", "", "t", "a", "b", "c"));

        List<TrackEntry> decoded = IndexCacheCodec.decode(text);

        assertEquals(1, decoded.size());
        assertEquals("/m/good.flac", decoded.get(0).path());
        assertEquals(new TrackTags("t", "a", "b", 3, "2000", true), decoded.get(0).tags());
    }

    @Test
    void windowsLineEndingsAreAccepted() {
        String text = IndexCacheCodec.HEADER + "\r\n" + line("/m/a.flac", "1", "2", "3", "0", "", "t", "a", "b") + "\r\n";

        assertEquals(1, IndexCacheCodec.decode(text).size());
    }

    @Test
    void headerCheckAcceptsOnlyTheCurrentVersion() {
        assertTrue(IndexCacheCodec.hasValidHeader(IndexCacheCodec.encode(List.of())));
        assertTrue(!IndexCacheCodec.hasValidHeader(null));
        assertTrue(!IndexCacheCodec.hasValidHeader("garbage"));
    }
}
