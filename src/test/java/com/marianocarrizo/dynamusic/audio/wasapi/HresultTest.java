package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import org.junit.jupiter.api.Test;

class HresultTest {

    private static final int UNSUPPORTED_FORMAT = 0x88890008;
    private static final int DEVICE_IN_USE = 0x8889000A;
    private static final int NOT_ALLOWED = 0x8889000E;
    private static final int NOT_ALIGNED = 0x88890019;
    private static final int DEVICE_INVALIDATED = 0x88890004;

    @Test
    void knownCodesMapToTheirNames() {
        assertEquals(Hresult.S_OK, Hresult.of(0));
        assertEquals(Hresult.AUDCLNT_E_UNSUPPORTED_FORMAT, Hresult.of(UNSUPPORTED_FORMAT));
        assertEquals(Hresult.AUDCLNT_E_DEVICE_IN_USE, Hresult.of(DEVICE_IN_USE));
        assertEquals(Hresult.AUDCLNT_E_BUFFER_SIZE_NOT_ALIGNED, Hresult.of(NOT_ALIGNED));
        assertEquals(Hresult.AUDCLNT_E_DEVICE_INVALIDATED, Hresult.of(DEVICE_INVALIDATED));
    }

    @Test
    void unknownCodesMapToUnknown() {
        assertEquals(Hresult.UNKNOWN, Hresult.of(0x12345678));
    }

    @Test
    void negativeCodesFailAndNonNegativeOnesDoNot() {
        assertTrue(Hresult.failed(UNSUPPORTED_FORMAT));
        assertFalse(Hresult.failed(0));
        assertFalse(Hresult.failed(1));
    }

    @Test
    void descriptionCarriesTheHexCodeAndTheName() {
        assertEquals("0x88890008 AUDCLNT_E_UNSUPPORTED_FORMAT", Hresult.describe(UNSUPPORTED_FORMAT));
    }

    @Test
    void checkThrowsOneUnavailableExceptionWithTheHexCode() {
        OutputUnavailableException failure = assertThrows(OutputUnavailableException.class,
                () -> Hresult.check("Initialize", DEVICE_IN_USE));

        assertTrue(failure.getMessage().contains("0x8889000A"));
        assertTrue(failure.getMessage().contains("Initialize"));
    }

    @Test
    void checkAcceptsSuccessCodes() {
        assertDoesNotThrow(() -> Hresult.check("Stop", 0));
        assertDoesNotThrow(() -> Hresult.check("Stop", 1));
    }

    @Test
    void probeFailuresMapToTheSupportLevel() {
        assertEquals(ExclusiveSupport.UNSUPPORTED, ExclusiveSupport.ofFailure(UNSUPPORTED_FORMAT));
        assertEquals(ExclusiveSupport.NOT_ALLOWED, ExclusiveSupport.ofFailure(NOT_ALLOWED));
        assertEquals(ExclusiveSupport.UNAVAILABLE, ExclusiveSupport.ofFailure(DEVICE_INVALIDATED));
        assertEquals(ExclusiveSupport.UNAVAILABLE, ExclusiveSupport.ofFailure(0x12345678));
    }
}
