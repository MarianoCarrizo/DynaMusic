package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import javafx.scene.control.Tab;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeTabsTest {

    private static final class FakeSurface implements ModeTabs.Surface {
        List<Tab> tabs = List.of();
        Tab selected;
        boolean swappingDuringShow;
        ModeTabs owner;

        @Override
        public Tab selected() {
            return selected;
        }

        @Override
        public void show(List<Tab> shown, Tab chosen) {
            swappingDuringShow = owner.isSwapping();
            tabs = shown;
            selected = chosen;
        }
    }

    private final Tab moods = tab("Moods", ModeMembership.SESSION_ONLY);
    private final Tab library = tab("Library", ModeMembership.LISTEN_ONLY);
    private final Tab playlists = tab("Playlists", null);
    private final FakeSurface surface = new FakeSurface();
    private final ModeTabs tabs = new ModeTabs(List.of(moods, library, playlists), surface);

    {
        surface.owner = tabs;
    }

    private static Tab tab(String title, ModeMembership membership) {
        Tab tab = new Tab(title);
        if (membership != null) tab.getStyleClass().add(membership.styleClass());
        return tab;
    }

    @Test
    void sessionShowsMoodsAndPlaylists() {
        tabs.show(AppMode.SESSION);

        assertEquals(List.of(moods, playlists), surface.tabs);
    }

    @Test
    void listenShowsLibraryAndPlaylists() {
        tabs.show(AppMode.LISTEN);

        assertEquals(List.of(library, playlists), surface.tabs);
    }

    @Test
    void firstShowKeepsTheTabAlreadySelected() {
        surface.selected = playlists;

        tabs.show(AppMode.SESSION);

        assertEquals(playlists, surface.selected);
    }

    @Test
    void eachModeRemembersItsOwnSelectedTab() {
        tabs.show(AppMode.SESSION);
        surface.selected = playlists;
        tabs.show(AppMode.LISTEN);
        surface.selected = library;

        tabs.show(AppMode.SESSION);
        assertEquals(playlists, surface.selected);

        tabs.show(AppMode.LISTEN);
        assertEquals(library, surface.selected);
    }

    @Test
    void modeWithoutAMemorySelectsItsFirstTab() {
        tabs.show(AppMode.SESSION);
        surface.selected = playlists;

        tabs.show(AppMode.LISTEN);

        assertEquals(library, surface.selected);
    }

    @Test
    void swappingIsReportedOnlyWhileTheSurfaceIsBeingChanged() {
        tabs.show(AppMode.SESSION);

        assertTrue(surface.swappingDuringShow);
        assertFalse(tabs.isSwapping());
    }
}
