package com.marianocarrizo.dynamusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.marianocarrizo.dynamusic.model.NowPlayingSource;
import org.junit.jupiter.api.Test;

class ReopenTargetTest {

    @Test
    void listenSourcesMapToTheirOwnTarget() {
        assertEquals(ReopenTarget.PREVIEW, ReopenTarget.of(NowPlayingSource.PREVIEW));
        assertEquals(ReopenTarget.PLAYLIST, ReopenTarget.of(NowPlayingSource.PLAYLIST));
        assertEquals(ReopenTarget.ON_DEMAND, ReopenTarget.of(NowPlayingSource.ON_DEMAND));
    }

    @Test
    void moodsAndNothingPlayingAreNeverReopened() {
        assertEquals(ReopenTarget.NONE, ReopenTarget.of(NowPlayingSource.MOOD));
        assertEquals(ReopenTarget.NONE, ReopenTarget.of(NowPlayingSource.NONE));
    }
}
