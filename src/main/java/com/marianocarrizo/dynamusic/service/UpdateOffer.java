package com.marianocarrizo.dynamusic.service;

import com.marianocarrizo.dynamusic.service.UpdateChecker.Version;

import java.util.Optional;

public final class UpdateOffer {

    private UpdateOffer() {}

    public static boolean isOffered(Version available, Optional<Version> skipped) {
        return skipped.isEmpty() || available.isNewerThan(skipped.get());
    }

    public static Optional<Version> skipped(String stored) {
        return Version.parse(stored);
    }
}
