package com.marianocarrizo.dynamusic.model;

import java.util.Objects;

public class BackgroundConfig {

    private String path;
    private String fitMode;
    private Long segmentStartMs;
    private Long segmentEndMs;
    private Double playbackRate;
    private Boolean boomerangEnabled;

    public BackgroundConfig() {}

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getFitMode() { return fitMode; }
    public void setFitMode(String fitMode) { this.fitMode = fitMode; }

    public Long getSegmentStartMs() { return segmentStartMs; }
    public void setSegmentStartMs(Long ms) { this.segmentStartMs = ms; }

    public Long getSegmentEndMs() { return segmentEndMs; }
    public void setSegmentEndMs(Long ms) { this.segmentEndMs = ms; }

    public Double getPlaybackRate() { return playbackRate; }
    public void setPlaybackRate(Double rate) { this.playbackRate = rate; }

    public Boolean getBoomerangEnabled() { return boomerangEnabled; }
    public void setBoomerangEnabled(Boolean v) { this.boomerangEnabled = v; }

    public boolean sameLook(BackgroundConfig other) {
        return other != null
                && Objects.equals(path, other.path)
                && Objects.equals(fitMode, other.fitMode)
                && Objects.equals(segmentStartMs, other.segmentStartMs)
                && Objects.equals(segmentEndMs, other.segmentEndMs)
                && effectiveRate() == other.effectiveRate()
                && Boolean.TRUE.equals(boomerangEnabled) == Boolean.TRUE.equals(other.boomerangEnabled);
    }

    private double effectiveRate() {
        return playbackRate != null ? playbackRate : 1.0;
    }

    public BackgroundConfig copy() {
        BackgroundConfig c = new BackgroundConfig();
        c.path = path;
        c.fitMode = fitMode;
        c.segmentStartMs = segmentStartMs;
        c.segmentEndMs = segmentEndMs;
        c.playbackRate = playbackRate;
        c.boomerangEnabled = boomerangEnabled;
        return c;
    }
}
