package com.marianocarrizo.dynamusic.model;

public enum LoadState {
    READY,
    LOADING,
    FAILED;

    public boolean isBusy() {
        return this != READY;
    }
}
