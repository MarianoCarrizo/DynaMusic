package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AudioFocusRequestTest {

    private boolean previewLoaded;
    private int previewPauses;
    private int modePauses;
    private AudioFocus focus;

    @BeforeEach
    void setUp() {
        previewLoaded = true;
        focus = new AudioFocus(() -> previewLoaded, () -> previewPauses++, () -> modePauses++);
    }

    @Test
    void requestingATrackSilencesEverythingThatWasSounding() {
        focus.claimForRequest();

        assertEquals(1, previewPauses);
        assertEquals(1, modePauses);
    }

    @Test
    void requestingATrackWithoutAPreviewStillSilencesTheModePlayers() {
        previewLoaded = false;

        focus.claimForRequest();

        assertEquals(0, previewPauses);
        assertEquals(1, modePauses);
    }

    @Test
    void aRequestEndsThePreviewPriority() {
        focus.claimForPreview();

        focus.claimForRequest();

        assertFalse(focus.previewSoundedLast());
    }

    @Test
    void aPendingPreviewIsInvalidatedByALaterRequest() {
        PreviewTicket ticket = focus.claimForNewPreview();

        focus.claimForRequest();

        assertEquals(PreviewReadiness.KEEP_PAUSED, focus.readinessOf(ticket));
    }
}
