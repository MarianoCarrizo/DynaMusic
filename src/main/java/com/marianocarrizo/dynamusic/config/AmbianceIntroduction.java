package com.marianocarrizo.dynamusic.config;

public final class AmbianceIntroduction {

    private AmbianceIntroduction() {
    }

    public static boolean isIntroduced(Boolean stored) {
        return stored == null || stored;
    }
}
