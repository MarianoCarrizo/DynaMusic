package com.marianocarrizo.dynamusic.service;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class TrackNames {

    private TrackNames() {
    }

    public static String title(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static String album(File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent == null || isManagedFolder(parent)) return "";
        return parent.getName();
    }

    private static boolean isManagedFolder(File folder) {
        return sameFolder(folder, TrackLibrary.libraryDir()) || sameFolder(folder, YouTubeDownloader.cacheDirPath());
    }

    private static boolean sameFolder(File folder, Path managed) {
        try {
            return folder.toPath().toAbsolutePath().normalize().equals(managed.toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
