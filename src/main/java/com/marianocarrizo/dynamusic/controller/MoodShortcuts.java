package com.marianocarrizo.dynamusic.controller;

final class MoodShortcuts {

    private MoodShortcuts() {
    }

    static boolean enabled(Object selectedTab, Object moodsTab) {
        return moodsTab != null && selectedTab == moodsTab;
    }
}
