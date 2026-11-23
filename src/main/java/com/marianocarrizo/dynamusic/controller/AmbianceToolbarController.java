package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.config.SessionConfig;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Screen;

import java.util.List;

public final class AmbianceToolbarController {

    interface Host {
        AmbianceCoordinator ambiance();

        SessionConfig session();

        void saveSession();
    }


    private static final int AUTO_SCREEN_MENU_INDEX = 0;

    @FXML private SplitMenuButton btnToggleAmbiance;

    private final ToggleGroup screenGroup = new ToggleGroup();
    private Host host;

    void attach(Host host) {
        this.host = host;
        host.ambiance().setOnVisibilityChanged(this::showVisibility);
        populateScreenMenu();
        Screen.getScreens().addListener((ListChangeListener<Screen>) change ->
            restoreScreen(host.session().getAmbianceScreenIndex()));
    }

    void restoreScreen(int savedScreenIndex) {
        populateScreenMenu();
        int menuTarget = savedScreenIndex < 0 ? AUTO_SCREEN_MENU_INDEX : savedScreenIndex + 1;
        selectScreenItem(menuTarget < screenItemCount() ? menuTarget : AUTO_SCREEN_MENU_INDEX);
        host.ambiance().applyScreen(savedScreenIndex);
    }

    @FXML
    private void onToggleAmbiance() {
        if (host.ambiance().isVisible()) host.ambiance().hide();
        else showAmbiance();
    }

    private void showAmbiance() {
        host.ambiance().show();
        if (host.session().isAmbianceIntroduced()) return;
        host.session().markAmbianceIntroduced();
        host.saveSession();
    }

    private void showVisibility(boolean showing) {
        btnToggleAmbiance.setText(showing ? "Hide ambiance" : "Show ambiance");
    }

    private void populateScreenMenu() {
        int previousIndex = selectedScreenItem();
        List<Screen> screens = Screen.getScreens();

        btnToggleAmbiance.getItems().clear();
        screenGroup.getToggles().clear();
        addScreenItem("Auto (recommended)", AUTO_SCREEN_MENU_INDEX);
        for (int i = 0; i < screens.size(); i++) {
            Screen s = screens.get(i);
            int w = (int) s.getBounds().getWidth();
            int h = (int) s.getBounds().getHeight();
            String tag = s.equals(Screen.getPrimary()) ? " (primary)" : "";
            addScreenItem("Screen " + (i + 1) + " — " + w + "x" + h + tag, i + 1);
        }

        selectScreenItem(previousIndex >= 0 && previousIndex < screenItemCount()
                ? previousIndex : AUTO_SCREEN_MENU_INDEX);
    }

    private void addScreenItem(String text, int menuIndex) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(screenGroup);
        item.setOnAction(e -> onScreenSelected(menuIndex));
        btnToggleAmbiance.getItems().add(item);
    }

    private int screenItemCount() {
        return btnToggleAmbiance.getItems().size();
    }

    private int selectedScreenItem() {
        Toggle selected = screenGroup.getSelectedToggle();
        return selected == null ? -1 : btnToggleAmbiance.getItems().indexOf(selected);
    }

    private void selectScreenItem(int index) {
        if (index < 0 || index >= screenItemCount()) return;
        ((RadioMenuItem) btnToggleAmbiance.getItems().get(index)).setSelected(true);
    }

    private void onScreenSelected(int menuIndex) {
        int resolvedIndex = menuIndex <= AUTO_SCREEN_MENU_INDEX ? -1 : menuIndex - 1;
        host.session().setAmbianceScreenIndex(resolvedIndex);
        host.saveSession();
        host.ambiance().applyScreen(resolvedIndex);
    }
}
