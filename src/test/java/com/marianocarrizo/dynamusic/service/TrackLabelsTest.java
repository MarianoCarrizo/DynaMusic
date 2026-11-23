package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.marianocarrizo.dynamusic.tags.TrackTags;
import java.io.File;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackLabelsTest {

    private static final File UNTAGGED = new File("music/Epic/Ultimate Song.mp3");

    private static Optional<TrackTags> tags(String title, String artist, String album) {
        return Optional.of(new TrackTags(title, artist, album, 0, "", false));
    }

    @Test
    void untaggedFileUsesFileNameAndFolder() {
        TrackLabels labels = TrackLabels.of(UNTAGGED, Optional.empty());

        assertEquals("Ultimate Song", labels.title());
        assertEquals("Epic", labels.detail());
    }

    @Test
    void artistAndAlbumAreJoinedWithTheSeparator() {
        assertEquals("Band · Rec", TrackLabels.of(UNTAGGED, tags("Song", "Band", "Rec")).detail());
        assertEquals("Song", TrackLabels.of(UNTAGGED, tags("Song", "Band", "Rec")).title());
    }

    @Test
    void onlyOneFieldHasNoSeparator() {
        assertEquals("Band · Epic", TrackLabels.of(UNTAGGED, tags("Song", "Band", "")).detail());
        assertEquals("Rec", TrackLabels.of(UNTAGGED, tags("", "", "Rec")).detail());
    }

    @Test
    void partialTagsFallBackFieldByField() {
        TrackLabels labels = TrackLabels.of(UNTAGGED, tags("Tagged", "", ""));

        assertEquals("Tagged", labels.title());
        assertEquals("Epic", labels.detail());
    }

    @Test
    void blankAndControlCharacterTitlesFallBackToTheFileName() {
        assertEquals("Ultimate Song", TrackLabels.of(UNTAGGED, tags("  \u0001 ", "", "")).title());
    }

    @Test
    void detailIsEmptyWhenNothingIsKnown() {
        assertEquals("", TrackLabels.detail("", ""));
        assertEquals("A", TrackLabels.detail("A", ""));
        assertEquals("B", TrackLabels.detail("", "B"));
    }

    @Test
    void markupCharactersStayLiteral() {
        TrackLabels labels = TrackLabels.of(UNTAGGED, tags("Rock & <Roll>", "AC/DC", "Black & Blue"));

        assertEquals("Rock & <Roll>", labels.title());
        assertEquals("AC/DC · Black & Blue", labels.detail());
    }
}
