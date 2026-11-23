package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFocusTest {

    private boolean previewLoaded;
    private int previewPauses;
    private int modePauses;
    private int previewPausedNotifications;
    private int modePausedNotifications;
    private AudioFocus focus;

    @BeforeEach
    void setUp() {
        previewLoaded = true;
        previewPauses = 0;
        modePauses = 0;
        previewPausedNotifications = 0;
        modePausedNotifications = 0;
        focus = new AudioFocus(() -> previewLoaded, () -> previewPauses++, () -> modePauses++);
        focus.setOnPreviewPausedByMode(() -> previewPausedNotifications++);
        focus.setOnModePausedByPreview(() -> modePausedNotifications++);
    }

    @Test
    void claimingForPreviewPausesModePlayersAndNotifies() {
        focus.claimForPreview();

        assertEquals(1, modePauses);
        assertEquals(1, modePausedNotifications);
        assertEquals(0, previewPauses);
    }

    @Test
    void claimingForModePausesTheLoadedPreviewAndNotifies() {
        focus.claimForMode();

        assertEquals(1, previewPauses);
        assertEquals(1, previewPausedNotifications);
        assertEquals(0, modePauses);
    }

    @Test
    void claimingForModeWithoutPreviewDoesNothing() {
        previewLoaded = false;

        focus.claimForMode();

        assertEquals(0, previewPauses);
        assertEquals(0, previewPausedNotifications);
    }

    @Test
    void previewSoundedLastAfterClaimingForPreview() {
        focus.claimForPreview();

        assertTrue(focus.previewSoundedLast());
    }

    @Test
    void previewNoLongerSoundedLastAfterClaimingForMode() {
        focus.claimForPreview();
        focus.claimForMode();

        assertFalse(focus.previewSoundedLast());
    }

    @Test
    void previewDidNotSoundLastOnceItIsGone() {
        focus.claimForPreview();
        previewLoaded = false;

        assertFalse(focus.previewSoundedLast());
    }

    @Test
    void nothingSoundedYetMeansNoPreview() {
        assertFalse(focus.previewSoundedLast());
    }

    @Test
    void clearingListenersIsAllowed() {
        focus.setOnPreviewPausedByMode(null);
        focus.setOnModePausedByPreview(null);

        focus.claimForPreview();
        focus.claimForMode();

        assertEquals(1, previewPauses);
        assertEquals(1, modePauses);
    }
}
