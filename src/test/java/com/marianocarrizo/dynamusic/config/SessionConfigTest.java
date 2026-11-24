package com.marianocarrizo.dynamusic.config;

import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import com.marianocarrizo.dynamusic.enums.TrackType;
import com.marianocarrizo.dynamusic.model.AuraPreset;
import com.marianocarrizo.dynamusic.model.AuraSlot;
import com.marianocarrizo.dynamusic.model.Scene;
import com.google.gson.Gson;
import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.RepeatMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionConfigTest {

    @Test
    void emptyConfigCreatesDefaultSceneWithAllAuras() {
        SessionConfig cfg = new SessionConfig();
        assertTrue(cfg.getScenes().isEmpty());

        Scene scene = cfg.getActiveScene();

        assertEquals("Default", scene.getName());
        assertEquals(1, cfg.getScenes().size());
        assertEquals(scene.getId(), cfg.getActiveSceneId());
    }

    @Test
    void addSceneAppendsWithoutChangingActiveScene() {
        SessionConfig cfg = new SessionConfig();
        Scene first = cfg.getActiveScene();

        Scene second = cfg.addScene("Boss");

        assertEquals(2, cfg.getScenes().size());
        assertNotEquals(first.getId(), second.getId());
        assertSame(first, cfg.getActiveScene());
    }

    @Test
    void duplicateSceneDeepCopiesUnderNewIdAndName() {
        SessionConfig cfg = new SessionConfig();
        Scene original = cfg.getActiveScene();
        original.setAssignment(TrackType.RAGED, "C:/music/raged.mp3");
        original.getBackground().setPath("C:/bg.png");
        original.getAura(AuraSlot.RAGED).setIntensity(0.9);

        Scene copy = cfg.duplicateScene(original.getId(), "Copy");

        assertNotNull(copy);
        assertNotEquals(original.getId(), copy.getId());
        assertEquals("Copy", copy.getName());
        assertEquals(2, cfg.getScenes().size());
        assertSame(copy, cfg.getScenes().get(1), "inserted right after the source");
        assertEquals("C:/music/raged.mp3", copy.getAssignments().get(TrackType.RAGED));
        assertEquals(0.9, copy.getAura(AuraSlot.RAGED).getIntensity(), 1e-9);

        copy.getBackground().setPath("C:/other.png");
        copy.getAura(AuraSlot.RAGED).setIntensity(0.1);
        copy.setAssignment(TrackType.RAGED, "C:/music/other.mp3");
        assertEquals("C:/bg.png", original.getBackground().getPath());
        assertEquals(0.9, original.getAura(AuraSlot.RAGED).getIntensity(), 1e-9);
        assertEquals("C:/music/raged.mp3", original.getAssignments().get(TrackType.RAGED));
    }

    @Test
    void duplicateUnknownSceneReturnsNull() {
        SessionConfig cfg = new SessionConfig();
        cfg.getActiveScene();
        assertNull(cfg.duplicateScene("nope", "x"));
        assertNull(cfg.duplicateScene(null, "x"));
    }

    @Test
    void renameSceneIgnoresBlankAndUnknown() {
        SessionConfig cfg = new SessionConfig();
        Scene s = cfg.getActiveScene();

        cfg.renameScene(s.getId(), "Renamed");
        assertEquals("Renamed", s.getName());

        cfg.renameScene(s.getId(), "   ");
        cfg.renameScene(s.getId(), null);
        cfg.renameScene("unknown", "Other");
        assertEquals("Renamed", s.getName());
    }

    @Test
    void deleteSceneRefusesTheLastOne() {
        SessionConfig cfg = new SessionConfig();
        Scene only = cfg.getActiveScene();

        assertFalse(cfg.deleteScene(only.getId()));
        assertEquals(1, cfg.getScenes().size());
    }

    @Test
    void deleteActiveSceneActivatesAnotherOne() {
        SessionConfig cfg = new SessionConfig();
        Scene first = cfg.getActiveScene();
        Scene second = cfg.addScene("Second");
        cfg.setActiveScene(second.getId());

        assertTrue(cfg.deleteScene(second.getId()));

        assertEquals(1, cfg.getScenes().size());
        assertEquals(first.getId(), cfg.getActiveSceneId());
        assertSame(first, cfg.getActiveScene());
    }

    @Test
    void deleteInactiveSceneKeepsActiveOne() {
        SessionConfig cfg = new SessionConfig();
        Scene first = cfg.getActiveScene();
        Scene second = cfg.addScene("Second");

        assertTrue(cfg.deleteScene(second.getId()));

        assertSame(first, cfg.getActiveScene());
        assertFalse(cfg.deleteScene("unknown"));
    }

    @Test
    void getActiveSceneFallsBackToFirstWhenIdIsInvalid() {
        SessionConfig cfg = new SessionConfig();
        Scene first = cfg.getActiveScene();
        cfg.addScene("Second");

        cfg.setActiveScene("does-not-exist");

        assertSame(first, cfg.getActiveScene());
        assertEquals(first.getId(), cfg.getActiveSceneId());
    }

    @Test
    void getScenesIsUnmodifiable() {
        SessionConfig cfg = new SessionConfig();
        cfg.getActiveScene();
        assertThrows(UnsupportedOperationException.class, () -> cfg.getScenes().clear());
    }

    @Test
    void adoptScenesKeepsScenesAndActiveId() {
        SessionConfig loaded = new SessionConfig();
        loaded.getActiveScene();
        Scene second = loaded.addScene("Second");
        loaded.setActiveScene(second.getId());

        SessionConfig target = new SessionConfig();
        target.getActiveScene();
        target.adoptScenesFrom(loaded);

        assertEquals(2, target.getScenes().size());
        assertEquals(second.getId(), target.getActiveSceneId());
        assertSame(second, target.getActiveScene());
    }

    @Test
    void adoptScenesFromNullOrEmptyChangesNothing() {
        SessionConfig target = new SessionConfig();
        Scene mine = target.getActiveScene();

        target.adoptScenesFrom(null);
        target.adoptScenesFrom(new SessionConfig());

        assertSame(mine, target.getActiveScene());
        assertEquals(1, target.getScenes().size());
    }

    @Test
    void facadeWritesIntoTheActiveScene() {
        SessionConfig cfg = new SessionConfig();
        Scene a = cfg.getActiveScene();
        Scene b = cfg.addScene("B");

        cfg.setBackgroundImagePath("C:/a.png");
        cfg.setVideoPlaybackRate(2.0);
        cfg.setActiveScene(b.getId());
        cfg.setBackgroundImagePath("C:/b.png");

        assertEquals("C:/a.png", a.getBackground().getPath());
        assertEquals("C:/b.png", cfg.getBackgroundImagePath());
        assertEquals(1.0, cfg.getVideoPlaybackRate(), "defaults to 1.0 on the other scene");
        assertEquals("FILL", cfg.getBackgroundFitMode());
    }

    @Test
    void defaultsForMissingValues() {
        SessionConfig cfg = new SessionConfig();
        assertEquals(2.0, cfg.getMoodSensitivity(TrackType.RAGED));
        assertEquals(2.0, cfg.getMoodSensitivity(null));
        assertEquals(1.0, cfg.getMasterVolume());
        assertEquals("MOOD", cfg.getActiveMode());
        assertTrue(cfg.getPlaylists().isEmpty());
        assertEquals(AuraPreset.SHADOW, cfg.getActiveScene().getAura(AuraSlot.PAUSED).getPreset());
    }

    @Test
    void clearBackgroundRemovesMediaAndVideoSettingsButKeepsFitMode() {
        SessionConfig cfg = new SessionConfig();
        cfg.setBackgroundImagePath("C:/bg/castle.mp4");
        cfg.setBackgroundFitMode("FIT");
        cfg.setVideoSegmentStartMs(1000L);
        cfg.setVideoSegmentEndMs(5000L);
        cfg.setVideoPlaybackRate(1.5);
        cfg.setBoomerangEnabled(true);

        cfg.clearBackground();

        assertNull(cfg.getBackgroundImagePath());
        assertNull(cfg.getVideoSegmentStartMs());
        assertNull(cfg.getVideoSegmentEndMs());
        assertFalse(Boolean.TRUE.equals(cfg.getBoomerangEnabled()));
        assertEquals("FIT", cfg.getBackgroundFitMode());
    }

    @Test
    void sessionWithoutRepeatDataStartsWithRepeatOff() {
        SessionConfig cfg = new Gson().fromJson("{}", SessionConfig.class);

        for (NowPlayingSource source : NowPlayingSource.values()) {
            assertEquals(RepeatMode.OFF, cfg.getRepeatMode(source));
        }
    }

    @Test
    void legacyBooleanRepeatFlagsMigrateToAllForPlaylistsAndOneForSingleTracks() {
        String json = "{\"repeatModes\":{\"PLAYLIST\":true,\"MOOD\":true,\"ON_DEMAND\":false}}";

        SessionConfig cfg = new Gson().fromJson(json, SessionConfig.class);

        assertEquals(RepeatMode.ALL, cfg.getRepeatMode(NowPlayingSource.PLAYLIST));
        assertEquals(RepeatMode.ONE, cfg.getRepeatMode(NowPlayingSource.MOOD));
        assertEquals(RepeatMode.OFF, cfg.getRepeatMode(NowPlayingSource.ON_DEMAND));
        assertEquals(RepeatMode.OFF, cfg.getRepeatMode(NowPlayingSource.PREVIEW));
    }

    @Test
    void unknownStoredRepeatValuesFallBackToOff() {
        SessionConfig cfg = new Gson().fromJson("{\"repeatModes\":{\"PLAYLIST\":\"sideways\"}}", SessionConfig.class);

        assertEquals(RepeatMode.OFF, cfg.getRepeatMode(NowPlayingSource.PLAYLIST));
    }

    @Test
    void repeatModesRoundTripAndAreNormalizedPerSource() {
        SessionConfig cfg = new SessionConfig();
        cfg.setRepeatMode(NowPlayingSource.PLAYLIST, RepeatMode.ONE);
        cfg.setRepeatMode(NowPlayingSource.MOOD, RepeatMode.ALL);
        Gson gson = new Gson();

        SessionConfig reloaded = gson.fromJson(gson.toJson(cfg), SessionConfig.class);

        assertEquals(RepeatMode.ONE, reloaded.getRepeatMode(NowPlayingSource.PLAYLIST));
        assertEquals(RepeatMode.ONE, reloaded.getRepeatMode(NowPlayingSource.MOOD));
    }

    @Test
    void audioCacheLimitDefaultsWhenTheSessionPredatesIt() {
        SessionConfig loaded = new Gson().fromJson("{\"masterVolume\":0.5}", SessionConfig.class);

        assertEquals(CacheLimit.GB_2, loaded.getAudioCacheLimit());
    }

    @Test
    void audioCacheLimitSurvivesAJsonRoundTrip() {
        SessionConfig cfg = new SessionConfig();
        cfg.setAudioCacheLimit(CacheLimit.GB_5);

        SessionConfig loaded = new Gson().fromJson(new Gson().toJson(cfg), SessionConfig.class);

        assertEquals(CacheLimit.GB_5, loaded.getAudioCacheLimit());
    }

    @Test
    void anUnknownPersistedLimitFallsBackToTheDefault() {
        SessionConfig loaded = new Gson().fromJson("{\"audioCacheLimit\":\"GB_999\"}", SessionConfig.class);

        assertEquals(CacheLimit.DEFAULT, loaded.getAudioCacheLimit());
    }

    @Test
    void adoptingACopiesTheLoadedLimitIntoTheLiveConfig() {
        SessionConfig loaded = new SessionConfig();
        loaded.setAudioCacheLimit(CacheLimit.GB_10);
        SessionConfig live = new SessionConfig();

        live.adoptAudioCacheLimitFrom(loaded);

        assertEquals(CacheLimit.GB_10, live.getAudioCacheLimit());
    }

    @Test
    void savingOtherSettingsDoesNotResetTheLimit() {
        SessionConfig cfg = new SessionConfig();
        cfg.setAudioCacheLimit(CacheLimit.GB_1);

        cfg.setAmbianceScreenIndex(2);

        assertEquals(CacheLimit.GB_1, cfg.getAudioCacheLimit());
    }

    @Test
    void settingNullRestoresTheDefault() {
        SessionConfig cfg = new SessionConfig();
        cfg.setAudioCacheLimit(CacheLimit.GB_20);

        cfg.setAudioCacheLimit(null);

        assertEquals(CacheLimit.DEFAULT, cfg.getAudioCacheLimit());
    }

    @Test
    void streamingEngineIsOffForAFreshConfig() {
        assertFalse(new SessionConfig().isStreamingEngineEnabled());
    }

    @Test
    void streamingEngineIsOffWhenTheSessionPredatesIt() {
        SessionConfig loaded = new Gson().fromJson("{\"masterVolume\":0.5}", SessionConfig.class);

        assertFalse(loaded.isStreamingEngineEnabled());
    }

    @Test
    void streamingEngineSurvivesAJsonRoundTrip() {
        SessionConfig cfg = new SessionConfig();
        cfg.setStreamingEngineEnabled(true);

        SessionConfig loaded = new Gson().fromJson(new Gson().toJson(cfg), SessionConfig.class);

        assertTrue(loaded.isStreamingEngineEnabled());
    }

    @Test
    void streamingEngineCanBeTurnedBackOff() {
        SessionConfig cfg = new SessionConfig();
        cfg.setStreamingEngineEnabled(true);

        cfg.setStreamingEngineEnabled(false);

        assertFalse(new Gson().fromJson(new Gson().toJson(cfg), SessionConfig.class).isStreamingEngineEnabled());
    }

    @Test
    void adoptingCopiesTheLoadedStreamingEngineIntoTheLiveConfig() {
        SessionConfig loaded = new SessionConfig();
        loaded.setStreamingEngineEnabled(true);
        SessionConfig live = new SessionConfig();

        live.adoptStreamingEngineFrom(loaded);

        assertTrue(live.isStreamingEngineEnabled());
    }

    @Test
    void savingOtherSettingsDoesNotResetTheStreamingEngine() {
        SessionConfig cfg = new SessionConfig();
        cfg.setStreamingEngineEnabled(true);

        cfg.setAudioCacheLimit(CacheLimit.GB_1);

        assertTrue(cfg.isStreamingEngineEnabled());
    }

    @Test
    void noUpdateIsSkippedForAFreshConfig() {
        assertNull(new SessionConfig().getSkippedUpdateVersion());
    }

    @Test
    void noUpdateIsSkippedWhenTheSessionPredatesIt() {
        SessionConfig loaded = new Gson().fromJson("{\"masterVolume\":0.5}", SessionConfig.class);

        assertNull(loaded.getSkippedUpdateVersion());
    }

    @Test
    void theSkippedUpdateVersionSurvivesAJsonRoundTrip() {
        SessionConfig cfg = new SessionConfig();
        cfg.setSkippedUpdateVersion("2.1.0");

        SessionConfig loaded = new Gson().fromJson(new Gson().toJson(cfg), SessionConfig.class);

        assertEquals("2.1.0", loaded.getSkippedUpdateVersion());
    }

    @Test
    void settingNullClearsTheSkippedUpdateVersion() {
        SessionConfig cfg = new SessionConfig();
        cfg.setSkippedUpdateVersion("2.1.0");

        cfg.setSkippedUpdateVersion(null);

        assertNull(cfg.getSkippedUpdateVersion());
    }

    @Test
    void adoptingCopiesTheLoadedSkippedUpdateVersionIntoTheLiveConfig() {
        SessionConfig loaded = new SessionConfig();
        loaded.setSkippedUpdateVersion("2.1.0");
        SessionConfig live = new SessionConfig();

        live.adoptSkippedUpdateVersionFrom(loaded);

        assertEquals("2.1.0", live.getSkippedUpdateVersion());
    }
}
