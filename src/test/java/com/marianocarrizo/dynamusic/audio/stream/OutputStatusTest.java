package com.marianocarrizo.dynamusic.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;

class OutputStatusTest {

    private static OutputStatus status(OutputRoute route, int bits, float rate) {
        return OutputStatus.of(route, PcmFormats.signedLittleEndian(bits, rate, 2));
    }

    @Test
    void exclusiveShowsBitDepthAndRate() {
        assertEquals("24/96 exclusive", status(OutputRoute.EXCLUSIVE, 24, 96_000).label());
    }

    @Test
    void sharedShowsBitDepthAndRate() {
        assertEquals("16/96 shared", status(OutputRoute.SHARED, 16, 96_000).label());
    }

    @Test
    void fractionalRatesKeepTheirDecimals() {
        assertEquals("16/44.1 exclusive", status(OutputRoute.EXCLUSIVE, 16, 44_100).label());
        assertEquals("24/88.2 shared", status(OutputRoute.SHARED, 24, 88_200).label());
    }

    @Test
    void attenuatedExclusiveIsNotBitPerfect() {
        OutputStatus attenuated = status(OutputRoute.EXCLUSIVE, 24, 96_000).withAttenuation(true);

        assertEquals("24/96 exclusive · not bit-perfect", attenuated.label());
        assertFalse(attenuated.bitPerfect());
    }

    @Test
    void returningToFullVolumeRestoresBitPerfect() {
        OutputStatus restored = status(OutputRoute.EXCLUSIVE, 24, 96_000).withAttenuation(true).withAttenuation(false);

        assertEquals("24/96 exclusive", restored.label());
        assertTrue(restored.bitPerfect());
    }

    @Test
    void sharedIsNeverBitPerfectAndAttenuationDoesNotChangeItsLabel() {
        OutputStatus shared = status(OutputRoute.SHARED, 16, 48_000);

        assertFalse(shared.bitPerfect());
        assertEquals(shared.label(), shared.withAttenuation(true).label());
    }

    @Test
    void noOutputHasNoLabel() {
        assertEquals("", OutputStatus.NONE.label());
        assertFalse(OutputStatus.NONE.bitPerfect());
    }

    @Test
    void statusIsBuiltFromTheFormat() {
        AudioFormat format = PcmFormats.signedLittleEndian(24, 192_000, 2);

        assertEquals(new OutputStatus(OutputRoute.EXCLUSIVE, 24, 192_000, false),
                OutputStatus.of(OutputRoute.EXCLUSIVE, format));
    }
}
