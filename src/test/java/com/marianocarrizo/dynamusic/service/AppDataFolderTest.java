package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AppDataFolderTest {

    @Test
    void rootUsesAppdataOnlyOnWindows() {
        assertEquals(Paths.get("C:\\Users\\u\\AppData\\Roaming", "DynaMusic"),
                AppDataFolder.root("Windows 11", "C:\\Users\\u\\AppData\\Roaming", "C:\\Users\\u"));
        assertEquals(Paths.get("/home/u", "DynaMusic"), AppDataFolder.root("Linux", "/stray/appdata", "/home/u"));
        assertEquals(Paths.get("/home/u", "DynaMusic"), AppDataFolder.root("Windows 11", " ", "/home/u"));
        assertEquals(Paths.get("/Users/u", "DynaMusic"), AppDataFolder.root("Mac OS X", null, "/Users/u"));
    }

    @Test
    void localRootPrefersLocalAppDataOnWindowsAndFallsBackElsewhere() {
        assertEquals(Paths.get("C:\\Users\\u\\AppData\\Local", "DynaMusic"), AppDataFolder.localRoot("Windows 11",
                "C:\\Users\\u\\AppData\\Local", "C:\\Users\\u\\AppData\\Roaming", "C:\\Users\\u"));
        assertEquals(Paths.get("C:\\Users\\u\\AppData\\Roaming", "DynaMusic"), AppDataFolder.localRoot("Windows 11",
                " ", "C:\\Users\\u\\AppData\\Roaming", "C:\\Users\\u"));
        assertEquals(Paths.get("/home/u", "DynaMusic"), AppDataFolder.localRoot("Linux", "/x", "/y", "/home/u"));
    }

    @Test
    void childResolvesInsideRoot() {
        assertEquals(AppDataFolder.root().resolve("audio"), AppDataFolder.child("audio"));
    }
}
