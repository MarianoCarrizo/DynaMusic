package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class AudioDevice {

    private static final Guid AUDIO_CLIENT_IID = new Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
    private static final int CLSCTX_ALL = 0x17;
    private static final int ACTIVATE_SLOT = 3;
    private static final ComMethod ACTIVATE = ComMethod.of(ACTIVATE_SLOT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

    private final ComObject object;

    AudioDevice(ComObject object) {
        this.object = object;
    }

    AudioClient activateClient(Arena arena) {
        MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
        Hresult.check("IMMDevice.Activate(IAudioClient)",
                ACTIVATE.invoke(object, AUDIO_CLIENT_IID.allocate(arena), CLSCTX_ALL, MemorySegment.NULL, output));
        return new AudioClient(ComObject.fromOutput(output), arena);
    }

    void release() {
        object.release();
    }
}
