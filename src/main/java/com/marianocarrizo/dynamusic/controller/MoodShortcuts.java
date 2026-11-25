package com.marianocarrizo.dynamusic.controller;

import com.marianocarrizo.dynamusic.model.AppMode;

final class MoodShortcuts {

    private MoodShortcuts() {
    }

    static boolean enabled(AppMode appMode, Object selectedTab, Object moodsTab) {
        return appMode == AppMode.SESSION && moodsTab != null && selectedTab == moodsTab;
    }
}
