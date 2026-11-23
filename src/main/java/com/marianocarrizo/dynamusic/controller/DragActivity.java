package com.marianocarrizo.dynamusic.controller;

final class DragActivity {

    private int active;

    void begin() {
        active++;
    }

    void end() {
        active = Math.max(0, active - 1);
    }

    boolean isActive() {
        return active > 0;
    }
}
