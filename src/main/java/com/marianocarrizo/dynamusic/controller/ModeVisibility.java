package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

final class ModeVisibility {

    private final List<Node> members = new ArrayList<>();
    private final Function<Node, List<? extends Node>> children;
    private final Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    ModeVisibility(Parent root) {
        this(root, ModeVisibility::childrenOf);
    }

    ModeVisibility(Node root, Function<Node, List<? extends Node>> children) {
        this.children = children;
        collect(root);
    }

    void apply(AppMode mode) {
        for (Node node : members) {
            boolean shown = ModeMembership.visibleIn(mode, node.getStyleClass());
            node.setVisible(shown);
            node.setManaged(shown);
        }
    }

    private void collect(Node node) {
        if (!seen.add(node)) return;
        if (ModeMembership.marked(node.getStyleClass())) members.add(node);
        children.apply(node).forEach(this::collect);
    }

    static List<? extends Node> childrenOf(Node node) {
        if (node instanceof SplitPane split) return split.getItems();
        if (node instanceof TabPane pane) {
            return pane.getTabs().stream().map(Tab::getContent).filter(Objects::nonNull).toList();
        }
        if (node instanceof ScrollPane scroll) {
            return scroll.getContent() == null ? List.of() : List.of(scroll.getContent());
        }
        if (node instanceof Parent parent) return parent.getChildrenUnmodifiable();
        return List.of();
    }
}
