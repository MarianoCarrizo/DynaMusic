package com.marianocarrizo.dynamusic.controller;

import java.io.File;

final class DisplayNames {

    private DisplayNames() {
    }

    static String capitalize(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String trackLabel(File file, String title) {
        return title != null && !title.isBlank() ? title : stripExtension(file.getName());
    }
}
