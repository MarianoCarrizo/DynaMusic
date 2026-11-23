package com.marianocarrizo.dynamusic.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbianceIntroductionTest {

    @Test
    void missingFieldCountsAsIntroducedSoExistingUsersKeepTheirBehavior() {
        assertTrue(AmbianceIntroduction.isIntroduced(null));
    }

    @Test
    void explicitTrueIsIntroduced() {
        assertTrue(AmbianceIntroduction.isIntroduced(Boolean.TRUE));
    }

    @Test
    void explicitFalseIsNotIntroduced() {
        assertFalse(AmbianceIntroduction.isIntroduced(Boolean.FALSE));
    }

    @Test
    void firstRunConfigIsNotIntroducedUntilMarked() {
        SessionConfig cfg = SessionConfig.firstRun();
        assertFalse(cfg.isAmbianceIntroduced());

        cfg.markAmbianceIntroduced();

        assertTrue(cfg.isAmbianceIntroduced());
    }

    @Test
    void plainConfigIsIntroduced() {
        assertTrue(new SessionConfig().isAmbianceIntroduced());
    }

    @Test
    void missingSessionFileLoadsAsFirstRun(@TempDir Path dir) {
        SessionConfig cfg = new SessionConfigManager(dir.resolve("session.json")).load();

        assertFalse(cfg.isAmbianceIntroduced());
    }

    @Test
    void existingSessionWithoutTheFieldLoadsAsIntroduced(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("session.json");
        Files.writeString(file, "{\"masterVolume\": 0.5}");

        SessionConfig cfg = new SessionConfigManager(file).load();

        assertTrue(cfg.isAmbianceIntroduced());
    }

    @Test
    void introductionSurvivesSaveAndLoad(@TempDir Path dir) {
        SessionConfigManager manager = new SessionConfigManager(dir.resolve("session.json"));
        SessionConfig cfg = SessionConfig.firstRun();
        manager.save(cfg);
        assertFalse(manager.load().isAmbianceIntroduced());

        cfg.markAmbianceIntroduced();
        manager.save(cfg);

        assertTrue(manager.load().isAmbianceIntroduced());
    }

    @Test
    void adoptingCopiesTheStoredValue() {
        SessionConfig target = new SessionConfig();

        target.adoptAmbianceIntroductionFrom(SessionConfig.firstRun());

        assertFalse(target.isAmbianceIntroduced());
    }
}
