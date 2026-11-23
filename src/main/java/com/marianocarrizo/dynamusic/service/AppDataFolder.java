package com.marianocarrizo.dynamusic.service;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppDataFolder {

    static final String FOLDER_NAME = "DynaMusic";

    private AppDataFolder() {}

    public static Path root() {
        return parent().resolve(FOLDER_NAME);
    }

    public static Path parent() {
        return parent(System.getProperty("os.name", ""), System.getenv("APPDATA"), System.getProperty("user.home"));
    }

    static Path root(String osName, String appdata, String home) {
        return parent(osName, appdata, home).resolve(FOLDER_NAME);
    }

    static Path parent(String osName, String appdata, String home) {
        return (YtDlpManager.isWindows(osName) && appdata != null && !appdata.isBlank())
                ? Paths.get(appdata)
                : Paths.get(home);
    }

    public static Path localRoot() {
        return localRoot(System.getProperty("os.name", ""), System.getenv("LOCALAPPDATA"),
                System.getenv("APPDATA"), System.getProperty("user.home"));
    }

    static Path localRoot(String osName, String localAppData, String appdata, String home) {
        boolean usable = YtDlpManager.isWindows(osName) && localAppData != null && !localAppData.isBlank();
        return usable ? Paths.get(localAppData).resolve(FOLDER_NAME) : root(osName, appdata, home);
    }

    public static Path child(String name) {
        return root().resolve(name);
    }
}
