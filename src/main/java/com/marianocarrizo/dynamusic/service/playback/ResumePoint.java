package com.marianocarrizo.dynamusic.service.playback;

import javafx.util.Duration;

public record ResumePoint(Duration position, boolean playing) {

    public static final ResumePoint START = new ResumePoint(Duration.ZERO, true);

    public static ResumePoint of(Playback player, boolean playing) {
        Duration at = player.getCurrentTime();
        boolean usable = at != null && !at.isUnknown() && !at.isIndefinite() && at.greaterThan(Duration.ZERO);
        return new ResumePoint(usable ? at : Duration.ZERO, playing);
    }

    public void applyTo(Playback player) {
        if (position.greaterThan(Duration.ZERO)) player.seek(position);
        if (playing) player.play();
    }
}
