package com.marianocarrizo.dynamusic.service;

final class LoadSequence {

    private int latest = 0;

    int begin() {
        return ++latest;
    }

    void invalidate() {
        latest++;
    }

    boolean isCurrent(int ticket) {
        return ticket == latest;
    }
}
