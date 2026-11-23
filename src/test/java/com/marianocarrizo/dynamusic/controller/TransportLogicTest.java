package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import com.marianocarrizo.dynamusic.model.RepeatMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportLogicTest {

    @Test
    void playlistEnablesEveryControl() {
        TransportAvailability availability = TransportAvailability.of(NowPlayingSource.PLAYLIST);

        assertTrue(availability.shuffle());
        assertTrue(availability.previous());
        assertTrue(availability.next());
        assertTrue(availability.repeat());
    }

    @Test
    void otherSourcesOnlyAllowRepeat() {
        for (NowPlayingSource source : List.of(NowPlayingSource.MOOD, NowPlayingSource.ON_DEMAND,
                NowPlayingSource.PREVIEW)) {
            TransportAvailability availability = TransportAvailability.of(source);

            assertFalse(availability.shuffle(), source.name());
            assertFalse(availability.previous(), source.name());
            assertFalse(availability.next(), source.name());
            assertTrue(availability.repeat(), source.name());
        }
    }

    @Test
    void nothingPlayingDisablesEverything() {
        TransportAvailability availability = TransportAvailability.of(NowPlayingSource.NONE);

        assertFalse(availability.shuffle());
        assertFalse(availability.previous());
        assertFalse(availability.next());
        assertFalse(availability.repeat());
    }

    @Test
    void repeatReachesOnlyTheTargetOfTheGivenSource() {
        List<String> calls = new ArrayList<>();
        RepeatSettings settings = new RepeatSettings(
                mode -> calls.add("mood=" + mode), mode -> calls.add("playlist=" + mode),
                mode -> calls.add("onDemand=" + mode), mode -> calls.add("preview=" + mode));

        settings.set(NowPlayingSource.PLAYLIST, RepeatMode.ALL);
        settings.set(NowPlayingSource.PREVIEW, RepeatMode.ONE);
        settings.set(NowPlayingSource.MOOD, RepeatMode.ONE);
        settings.set(NowPlayingSource.ON_DEMAND, RepeatMode.ONE);
        settings.set(NowPlayingSource.PLAYLIST, RepeatMode.OFF);

        assertEquals(List.of("playlist=ALL", "preview=ONE", "mood=ONE", "onDemand=ONE", "playlist=OFF"), calls);
    }

    @Test
    void repeatStateIsKeptPerSource() {
        RepeatSettings settings = new RepeatSettings(mode -> { }, mode -> { }, mode -> { }, mode -> { });

        settings.set(NowPlayingSource.MOOD, RepeatMode.ONE);

        assertEquals(RepeatMode.ONE, settings.modeOf(NowPlayingSource.MOOD));
        assertEquals(RepeatMode.OFF, settings.modeOf(NowPlayingSource.PLAYLIST));
    }

    @Test
    void nothingPlayingHasNoRepeatTarget() {
        List<String> calls = new ArrayList<>();
        RepeatSettings settings = new RepeatSettings(mode -> calls.add("mood"), mode -> calls.add("playlist"),
                mode -> calls.add("onDemand"), mode -> calls.add("preview"));

        settings.set(NowPlayingSource.NONE, RepeatMode.ONE);

        assertTrue(calls.isEmpty());
        assertEquals(RepeatMode.OFF, settings.modeOf(NowPlayingSource.NONE));
    }

    @Test
    void playlistRepeatCyclesOffAllOneOff() {
        RepeatSettings settings = new RepeatSettings(mode -> { }, mode -> { }, mode -> { }, mode -> { });

        assertEquals(RepeatMode.ALL, settings.cycle(NowPlayingSource.PLAYLIST));
        assertEquals(RepeatMode.ONE, settings.cycle(NowPlayingSource.PLAYLIST));
        assertEquals(RepeatMode.OFF, settings.cycle(NowPlayingSource.PLAYLIST));
    }

    @Test
    void singleTrackSourcesCycleOnlyOffAndOne() {
        RepeatSettings settings = new RepeatSettings(mode -> { }, mode -> { }, mode -> { }, mode -> { });

        for (NowPlayingSource source : List.of(NowPlayingSource.MOOD, NowPlayingSource.ON_DEMAND,
                NowPlayingSource.PREVIEW)) {
            assertEquals(RepeatMode.ONE, settings.cycle(source));
            assertEquals(RepeatMode.OFF, settings.cycle(source));
        }
    }

    @Test
    void repeatAllIsAppliedAsOneToSingleTrackSources() {
        List<RepeatMode> applied = new ArrayList<>();
        RepeatSettings settings = new RepeatSettings(applied::add, mode -> { }, mode -> { }, mode -> { });

        settings.set(NowPlayingSource.MOOD, RepeatMode.ALL);

        assertEquals(List.of(RepeatMode.ONE), applied);
        assertEquals(RepeatMode.ONE, settings.modeOf(NowPlayingSource.MOOD));
    }

    @Test
    void volumeLevelFollowsTheVolume() {
        assertEquals(VolumeLevel.MUTED, VolumeLevel.of(0));
        assertEquals(VolumeLevel.LOW, VolumeLevel.of(0.2));
        assertEquals(VolumeLevel.HIGH, VolumeLevel.of(0.5));
        assertEquals(VolumeLevel.HIGH, VolumeLevel.of(1));
    }
}
