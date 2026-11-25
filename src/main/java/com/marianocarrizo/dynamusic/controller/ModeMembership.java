package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;

import java.util.Collection;

enum ModeMembership {
    SESSION_ONLY("session-only", AppMode.SESSION),
    LISTEN_ONLY("listen-only", AppMode.LISTEN);

    private final String styleClass;
    private final AppMode mode;

    ModeMembership(String styleClass, AppMode mode) {
        this.styleClass = styleClass;
        this.mode = mode;
    }

    String styleClass() {
        return styleClass;
    }

    static boolean marked(Collection<String> styleClasses) {
        for (ModeMembership membership : values()) {
            if (styleClasses.contains(membership.styleClass)) return true;
        }
        return false;
    }

    static boolean visibleIn(AppMode mode, Collection<String> styleClasses) {
        for (ModeMembership membership : values()) {
            if (styleClasses.contains(membership.styleClass) && membership.mode != mode) return false;
        }
        return true;
    }
}
