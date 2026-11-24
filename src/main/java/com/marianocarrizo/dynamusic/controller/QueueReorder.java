package com.marianocarrizo.dynamusic.controller;

final class QueueReorder {

    private QueueReorder() {
    }

    static int targetIndex(int from, int slot) {
        return slot > from ? slot - 1 : slot;
    }
}
