package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.util.ArrayList;
import java.util.List;

final class ModeVisibility {

    private final List<Node> members = new ArrayList<>();

    ModeVisibility(Parent root) {
        for (ModeMembership membership : ModeMembership.values()) {
            members.addAll(root.lookupAll(membership.selector()));
        }
    }

    void apply(AppMode mode) {
        for (Node node : members) {
            boolean shown = ModeMembership.visibleIn(mode, node.getStyleClass());
            node.setVisible(shown);
            node.setManaged(shown);
        }
    }
}
