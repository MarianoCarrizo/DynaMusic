package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateChoiceTest {

    @Test
    void installingNowAndOnCloseArmTheInstaller() {
        assertTrue(UpdateChoice.INSTALL_NOW.armsInstaller());
        assertTrue(UpdateChoice.INSTALL_ON_CLOSE.armsInstaller());
    }

    @Test
    void decliningNeverArmsTheInstaller() {
        assertFalse(UpdateChoice.NOT_NOW.armsInstaller());
        assertFalse(UpdateChoice.SKIP_VERSION.armsInstaller());
    }

    @Test
    void onlySkippingRemembersTheVersion() {
        for (UpdateChoice choice : UpdateChoice.values()) {
            assertEquals(choice == UpdateChoice.SKIP_VERSION, choice.remembersVersion(), choice.name());
        }
    }
}
