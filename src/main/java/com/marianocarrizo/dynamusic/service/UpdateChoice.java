package com.marianocarrizo.dynamusic.service;

public enum UpdateChoice {
    INSTALL_NOW(true, false),
    INSTALL_ON_CLOSE(true, false),
    NOT_NOW(false, false),
    SKIP_VERSION(false, true);

    private final boolean armsInstaller;
    private final boolean remembersVersion;

    UpdateChoice(boolean armsInstaller, boolean remembersVersion) {
        this.armsInstaller = armsInstaller;
        this.remembersVersion = remembersVersion;
    }

    public boolean armsInstaller() { return armsInstaller; }

    public boolean remembersVersion() { return remembersVersion; }
}
