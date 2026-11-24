package com.marianocarrizo.dynamusic.controller;

import java.text.Normalizer;
import java.util.Locale;

final class NameNormalizer {

    private NameNormalizer() {
    }

    static String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        StringBuilder plain = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char letter = decomposed.charAt(i);
            if (Character.getType(letter) != Character.NON_SPACING_MARK) plain.append(letter);
        }
        return plain.toString().toLowerCase(Locale.ROOT);
    }
}
