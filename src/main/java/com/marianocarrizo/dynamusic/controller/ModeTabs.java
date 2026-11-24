package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ModeTabs {

    interface Surface {
        Tab selected();

        void show(List<Tab> tabs, Tab selected);
    }

    private final Surface surface;
    private final List<Tab> declared;
    private final Map<AppMode, Tab> remembered = new EnumMap<>(AppMode.class);
    private AppMode shown;
    private boolean swapping;

    ModeTabs(List<Tab> declared, Surface surface) {
        this.declared = List.copyOf(declared);
        this.surface = surface;
    }

    static ModeTabs of(TabPane pane) {
        return new ModeTabs(pane.getTabs(), new Surface() {
            @Override
            public Tab selected() {
                return pane.getSelectionModel().getSelectedItem();
            }

            @Override
            public void show(List<Tab> tabs, Tab selected) {
                pane.getTabs().setAll(tabs);
                pane.getSelectionModel().select(selected);
            }
        });
    }

    void show(AppMode mode) {
        Tab current = surface.selected();
        if (shown != null) remembered.put(shown, current);
        List<Tab> visible = declared.stream()
                .filter(tab -> ModeMembership.visibleIn(mode, tab.getStyleClass()))
                .toList();
        swapping = true;
        try {
            surface.show(visible, choose(mode, current, visible));
        } finally {
            swapping = false;
        }
        shown = mode;
    }

    boolean isSwapping() {
        return swapping;
    }

    private Tab choose(AppMode mode, Tab current, List<Tab> visible) {
        Tab wanted = shown == null ? current : remembered.get(mode);
        return wanted != null && visible.contains(wanted) ? wanted : visible.get(0);
    }
}
