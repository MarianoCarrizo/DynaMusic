package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SampleContainerTest {

    private static final int[] EXTREMES_24 = {0, 1, -1, 0x7FFFFF, -0x800000, 0x123456, -0x123456};
    private static final int[] EXTREMES_16 = {0, 1, -1, 0x7FFF, -0x8000, 0x1234, -0x1234};

    private static int[] repack(SampleContainer container, int sourceBits, int[] samples, int targetBytes) {
        byte[] source = PcmFrames.pack(sourceBits / 8, samples);
        byte[] target = new byte[samples.length * targetBytes];
        int written = container.repack(source, 0, sourceBits, samples.length, target, 0);
        assertEquals(target.length, written);
        return PcmFrames.unpack(targetBytes, target);
    }

    @Test
    void packed24SamplesAreLeftJustifiedInThirtyTwoBitsWithoutScaling() {
        int[] device = repack(SampleContainer.PADDED_32, 24, EXTREMES_24, 4);

        for (int i = 0; i < EXTREMES_24.length; i++) {
            assertEquals(EXTREMES_24[i] << 8, device[i]);
            assertEquals(EXTREMES_24[i], device[i] >> 8);
        }
    }

    @Test
    void sixteenBitSamplesAreLeftJustifiedInThirtyTwoBitsLosslessly() {
        int[] device = repack(SampleContainer.PADDED_32, 16, EXTREMES_16, 4);

        for (int i = 0; i < EXTREMES_16.length; i++) {
            assertEquals(EXTREMES_16[i] << 16, device[i]);
            assertEquals(EXTREMES_16[i], device[i] >> 16);
        }
    }

    @Test
    void thirtyTwoBitContainerLeavesTheLowByteSilent() {
        byte[] source = PcmFrames.pack(3, 0x7FFFFF);
        byte[] target = new byte[] {1, 1, 1, 1};

        SampleContainer.PADDED_32.repack(source, 0, 24, 1, target, 0);

        assertArrayEquals(new byte[] {0, (byte) 0xFF, (byte) 0xFF, 0x7F}, target);
    }

    @Test
    void sameWidthContainersCopyTheBytesUntouched() {
        assertArrayEquals(EXTREMES_24, repack(SampleContainer.PACKED_24, 24, EXTREMES_24, 3));
        assertArrayEquals(EXTREMES_16, repack(SampleContainer.PACKED_16, 16, EXTREMES_16, 2));
    }

    @Test
    void repackHonoursSourceAndTargetOffsets() {
        byte[] source = PcmFrames.pack(2, 9, 8, 7, 6);
        byte[] target = new byte[12];

        SampleContainer.PADDED_32.repack(source, 4, 16, 2, target, 4);

        assertArrayEquals(new int[] {0, 7 << 16, 6 << 16}, PcmFrames.unpack(4, target));
    }

    @Test
    void aContainerRefusesSourcesItCannotCarryExactly() {
        byte[] target = new byte[8];

        assertThrows(IllegalArgumentException.class,
                () -> SampleContainer.PACKED_16.repack(new byte[3], 0, 24, 1, target, 0));
        assertThrows(IllegalArgumentException.class,
                () -> SampleContainer.PACKED_24.repack(new byte[2], 0, 16, 1, target, 0));
    }

    @Test
    void twentyFourBitSourcesTryTheThirtyTwoBitContainerFirstThenPacked() {
        assertEquals(List.of(SampleContainer.PADDED_32, SampleContainer.PACKED_24), SampleContainer.candidates(24));
    }

    @Test
    void sixteenBitSourcesTryNativeSixteenThenThirtyTwoBit() {
        assertEquals(List.of(SampleContainer.PACKED_16, SampleContainer.PADDED_32), SampleContainer.candidates(16));
    }

    @Test
    void otherDepthsHaveNoCandidates() {
        assertTrue(SampleContainer.candidates(8).isEmpty());
        assertTrue(SampleContainer.candidates(32).isEmpty());
        assertFalse(SampleContainer.isSourceSupported(32));
        assertTrue(SampleContainer.isSourceSupported(24));
    }

    @Test
    void theDeviceAcceptingThirtyTwoBitWinsForTwentyFourBitSources() {
        Optional<SampleContainer> chosen = SampleContainer.choose(24, candidate -> true);

        assertEquals(Optional.of(SampleContainer.PADDED_32), chosen);
    }

    @Test
    void packedTwentyFourIsChosenWhenThirtyTwoBitIsRejected() {
        Optional<SampleContainer> chosen = SampleContainer.choose(24, candidate -> candidate == SampleContainer.PACKED_24);

        assertEquals(Optional.of(SampleContainer.PACKED_24), chosen);
    }

    @Test
    void nothingIsChosenWhenEveryLayoutIsRejected() {
        assertTrue(SampleContainer.choose(24, candidate -> false).isEmpty());
    }

    @Test
    void sixteenBitSourceUsesNativeSixteenWhenTheDeviceAcceptsIt() {
        assertEquals(Optional.of(SampleContainer.PACKED_16), SampleContainer.choose(16, candidate -> true));
    }

    @Test
    void sixteenBitSourceFallsToThirtyTwoBitWhenSixteenIsRejected() {
        Optional<SampleContainer> chosen = SampleContainer.choose(16, candidate -> candidate == SampleContainer.PADDED_32);

        assertEquals(Optional.of(SampleContainer.PADDED_32), chosen);
    }

    @Test
    void containerGeometryMatchesTheDeviceLayouts() {
        assertEquals(4, SampleContainer.PADDED_32.bytes());
        assertEquals(24, SampleContainer.PADDED_32.validBits());
        assertEquals(32, SampleContainer.PADDED_32.containerBits());
        assertEquals(3, SampleContainer.PACKED_24.bytes());
        assertEquals(2, SampleContainer.PACKED_16.bytes());
    }
}
