package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.NowPlayingSource;
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
                on -> calls.add("mood=" + on), on -> calls.add("playlist=" + on),
                on -> calls.add("onDemand=" + on), on -> calls.add("preview=" + on));

        settings.set(NowPlayingSource.PLAYLIST, true);
        settings.set(NowPlayingSource.PREVIEW, true);
        settings.set(NowPlayingSource.MOOD, true);
        settings.set(NowPlayingSource.ON_DEMAND, true);
        settings.set(NowPlayingSource.PLAYLIST, false);

        assertEquals(List.of("playlist=true", "preview=true", "mood=true", "onDemand=true", "playlist=false"), calls);
    }

    @Test
    void repeatStateIsKeptPerSource() {
        RepeatSettings settings = new RepeatSettings(on -> { }, on -> { }, on -> { }, on -> { });

        settings.set(NowPlayingSource.MOOD, true);

        assertTrue(settings.isOn(NowPlayingSource.MOOD));
        assertFalse(settings.isOn(NowPlayingSource.PLAYLIST));
    }

    @Test
    void nothingPlayingHasNoRepeatTarget() {
        List<String> calls = new ArrayList<>();
        RepeatSettings settings = new RepeatSettings(on -> calls.add("mood"), on -> calls.add("playlist"),
                on -> calls.add("onDemand"), on -> calls.add("preview"));

        settings.set(NowPlayingSource.NONE, true);

        assertTrue(calls.isEmpty());
        assertFalse(settings.isOn(NowPlayingSource.NONE));
    }

    @Test
    void volumeLevelFollowsTheVolume() {
        assertEquals(VolumeLevel.MUTED, VolumeLevel.of(0));
        assertEquals(VolumeLevel.LOW, VolumeLevel.of(0.2));
        assertEquals(VolumeLevel.HIGH, VolumeLevel.of(0.5));
        assertEquals(VolumeLevel.HIGH, VolumeLevel.of(1));
    }
}
