package com.marianocarrizo.dynamusic.model;

import com.marianocarrizo.dynamusic.enums.TrackType;

public enum AuraSlot {
    PAUSED,
    NORMAL,
    RAGED,
    ADVANTAGE,
    ON_DEMAND;

    public static AuraSlot of(TrackType type) {
        if (type == null) return ON_DEMAND;
        return switch (type) {
            case PAUSED    -> PAUSED;
            case NORMAL    -> NORMAL;
            case RAGED     -> RAGED;
            case ADVANTAGE -> ADVANTAGE;
        };
    }
}
