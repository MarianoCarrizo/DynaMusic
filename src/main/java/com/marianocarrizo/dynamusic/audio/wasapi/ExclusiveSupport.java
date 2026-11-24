package com.marianocarrizo.dynamusic.audio.wasapi;

public enum ExclusiveSupport {
    SUPPORTED,
    UNSUPPORTED,
    NOT_ALLOWED,
    UNAVAILABLE;

    static ExclusiveSupport ofFailure(int hresult) {
        return switch (Hresult.of(hresult)) {
            case AUDCLNT_E_UNSUPPORTED_FORMAT -> UNSUPPORTED;
            case AUDCLNT_E_EXCLUSIVE_MODE_NOT_ALLOWED -> NOT_ALLOWED;
            default -> UNAVAILABLE;
        };
    }
}
