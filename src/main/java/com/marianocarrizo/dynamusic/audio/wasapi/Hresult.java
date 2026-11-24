package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;

enum Hresult {
    S_OK(0),
    S_FALSE(1),
    AUDCLNT_E_NOT_INITIALIZED(0x88890001),
    AUDCLNT_E_ALREADY_INITIALIZED(0x88890002),
    AUDCLNT_E_DEVICE_INVALIDATED(0x88890004),
    AUDCLNT_E_BUFFER_TOO_LARGE(0x88890006),
    AUDCLNT_E_UNSUPPORTED_FORMAT(0x88890008),
    AUDCLNT_E_INVALID_SIZE(0x88890009),
    AUDCLNT_E_DEVICE_IN_USE(0x8889000A),
    AUDCLNT_E_EXCLUSIVE_MODE_NOT_ALLOWED(0x8889000E),
    AUDCLNT_E_ENDPOINT_CREATE_FAILED(0x8889000F),
    AUDCLNT_E_SERVICE_NOT_RUNNING(0x88890010),
    AUDCLNT_E_BUFDURATION_PERIOD_NOT_EQUAL(0x88890013),
    AUDCLNT_E_EVENTHANDLE_NOT_SET(0x88890014),
    AUDCLNT_E_BUFFER_SIZE_ERROR(0x88890016),
    AUDCLNT_E_BUFFER_SIZE_NOT_ALIGNED(0x88890019),
    AUDCLNT_E_INVALID_DEVICE_PERIOD(0x8889001D),
    AUDCLNT_E_RESOURCES_INVALIDATED(0x88890020),
    E_ACCESSDENIED(0x80070005),
    E_INVALIDARG(0x80070057),
    E_NOINTERFACE(0x80004002),
    RPC_E_CHANGED_MODE(0x80010106),
    UNKNOWN(-1);

    private final int code;

    Hresult(int code) {
        this.code = code;
    }

    static Hresult of(int code) {
        for (Hresult known : values()) {
            if (known != UNKNOWN && known.code == code) return known;
        }
        return UNKNOWN;
    }

    static boolean failed(int code) {
        return code < 0;
    }

    static String describe(int code) {
        return String.format("0x%08X %s", code, of(code));
    }

    static void check(String operation, int code) {
        if (failed(code)) throw new OutputUnavailableException(operation + " failed: " + describe(code));
    }
}
