package com.marianocarrizo.dynamusic.service;

import java.util.function.BooleanSupplier;

final class AudioFocus {

    private final BooleanSupplier previewLoaded;
    private final Runnable pausePreview;
    private final Runnable pauseModePlayers;

    private boolean previewSoundedLast = false;
    private int previewRequests = 0;
    private int modeClaims = 0;
    private Runnable onPreviewPausedByMode = () -> { };
    private Runnable onModePausedByPreview = () -> { };

    AudioFocus(BooleanSupplier previewLoaded, Runnable pausePreview, Runnable pauseModePlayers) {
        this.previewLoaded = previewLoaded;
        this.pausePreview = pausePreview;
        this.pauseModePlayers = pauseModePlayers;
    }

    void claimForMode() {
        previewSoundedLast = false;
        modeClaims++;
        if (previewLoaded.getAsBoolean()) {
            pausePreview.run();
            onPreviewPausedByMode.run();
        }
    }

    void claimForPreview() {
        previewSoundedLast = true;
        pauseModePlayers.run();
        onModePausedByPreview.run();
    }

    PreviewTicket claimForNewPreview() {
        previewRequests++;
        claimForPreview();
        return new PreviewTicket(previewRequests, modeClaims);
    }

    PreviewReadiness readinessOf(PreviewTicket ticket) {
        if (ticket.request() != previewRequests) return PreviewReadiness.DISCARD;
        if (ticket.modeClaims() != modeClaims) return PreviewReadiness.KEEP_PAUSED;
        return PreviewReadiness.PLAY;
    }

    boolean previewSoundedLast() {
        return previewSoundedLast && previewLoaded.getAsBoolean();
    }

    void setOnPreviewPausedByMode(Runnable listener) {
        onPreviewPausedByMode = listener == null ? () -> { } : listener;
    }

    void setOnModePausedByPreview(Runnable listener) {
        onModePausedByPreview = listener == null ? () -> { } : listener;
    }
}
