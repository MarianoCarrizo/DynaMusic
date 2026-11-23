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
    private AudioFocus focus;

    @BeforeEach
    void setUp() {
        previewLoaded = true;
        previewPauses = 0;
        modePauses = 0;
        focus = new AudioFocus(() -> previewLoaded, () -> previewPauses++, () -> modePauses++);
    }

    @Test
    void claimingForPreviewPausesModePlayers() {
        focus.claimForPreview();

        assertEquals(1, modePauses);
        assertEquals(0, previewPauses);
    }

    @Test
    void claimingForModePausesTheLoadedPreview() {
        focus.claimForMode();

        assertEquals(1, previewPauses);
        assertEquals(0, modePauses);
    }

    @Test
    void claimingForModeWithoutPreviewDoesNothing() {
        previewLoaded = false;

        focus.claimForMode();

        assertEquals(0, previewPauses);
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
    void aPreviewThatIsStillTheLatestRequestPlaysWhenReady() {
        PreviewTicket ticket = focus.claimForNewPreview();

        assertEquals(PreviewReadiness.PLAY, focus.readinessOf(ticket));
    }

    @Test
    void aPreviewStaysPausedWhenAModePlayerClaimedAudioWhileItLoaded() {
        PreviewTicket ticket = focus.claimForNewPreview();
        focus.claimForMode();

        assertEquals(PreviewReadiness.KEEP_PAUSED, focus.readinessOf(ticket));
    }

    @Test
    void anOlderPreviewRequestIsDiscardedWhenANewerOneStarted() {
        PreviewTicket older = focus.claimForNewPreview();
        PreviewTicket newer = focus.claimForNewPreview();

        assertEquals(PreviewReadiness.DISCARD, focus.readinessOf(older));
        assertEquals(PreviewReadiness.PLAY, focus.readinessOf(newer));
    }

    @Test
    void resumingTheLoadedPreviewDoesNotInvalidateItsTicket() {
        PreviewTicket ticket = focus.claimForNewPreview();
        focus.claimForPreview();

        assertEquals(PreviewReadiness.PLAY, focus.readinessOf(ticket));
    }

    @Test
    void aModeClaimBeforeTheNewPreviewDoesNotBlockIt() {
        focus.claimForMode();
        PreviewTicket ticket = focus.claimForNewPreview();

        assertEquals(PreviewReadiness.PLAY, focus.readinessOf(ticket));
    }

    @Test
    void aNewPreviewRequestPausesModePlayersAndSoundsLast() {
        focus.claimForNewPreview();

        assertEquals(1, modePauses);
        assertTrue(focus.previewSoundedLast());
    }
}
