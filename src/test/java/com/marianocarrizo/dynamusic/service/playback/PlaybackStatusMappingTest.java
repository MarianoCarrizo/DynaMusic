package com.marianocarrizo.dynamusic.service.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javafx.scene.media.MediaPlayer;
import org.junit.jupiter.api.Test;

class PlaybackStatusMappingTest {

    @Test
    void everyMediaPlayerStatusMapsToAPlaybackStatus() {
        for (MediaPlayer.Status status : MediaPlayer.Status.values()) {
            assertNotNull(PlaybackStatus.from(status), status.name());
        }
    }

    @Test
    void namesMatchOneToOne() {
        for (MediaPlayer.Status status : MediaPlayer.Status.values()) {
            assertEquals(status.name(), PlaybackStatus.from(status).name());
        }
    }

    @Test
    void aMissingStatusIsUnknown() {
        assertEquals(PlaybackStatus.UNKNOWN, PlaybackStatus.from(null));
    }
}
