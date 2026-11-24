package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeVisibilityTest {

    private final Pane root = new Pane();
    private final Pane sessionOnly = marked(ModeMembership.SESSION_ONLY);
    private final Pane listenOnly = marked(ModeMembership.LISTEN_ONLY);
    private final Pane shared = new Pane();

    private Pane marked(ModeMembership membership) {
        Pane pane = new Pane();
        pane.getStyleClass().add(membership.styleClass());
        root.getChildren().add(pane);
        return pane;
    }

    private ModeVisibility visibility() {
        root.getChildren().add(shared);
        return new ModeVisibility(root);
    }

    @Test
    void listenHidesSessionOnlyNodesAndTakesThemOutOfLayout() {
        visibility().apply(AppMode.LISTEN);

        assertFalse(sessionOnly.isVisible());
        assertFalse(sessionOnly.isManaged());
        assertTrue(listenOnly.isVisible());
        assertTrue(listenOnly.isManaged());
    }

    @Test
    void sessionHidesListenOnlyNodes() {
        visibility().apply(AppMode.SESSION);

        assertTrue(sessionOnly.isVisible());
        assertFalse(listenOnly.isVisible());
        assertFalse(listenOnly.isManaged());
    }

    @Test
    void returningToSessionShowsSessionOnlyNodesAgain() {
        ModeVisibility visibility = visibility();
        visibility.apply(AppMode.LISTEN);

        visibility.apply(AppMode.SESSION);

        assertTrue(sessionOnly.isVisible());
        assertTrue(sessionOnly.isManaged());
    }

    @Test
    void unmarkedNodesAreNeverTouched() {
        ModeVisibility visibility = visibility();

        visibility.apply(AppMode.LISTEN);
        assertTrue(shared.isVisible());
        assertTrue(shared.isManaged());

        visibility.apply(AppMode.SESSION);
        assertTrue(shared.isVisible());
    }
}
