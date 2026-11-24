package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class RenderClient {

    private static final int GET_BUFFER_SLOT = 3;
    private static final int RELEASE_BUFFER_SLOT = 4;
    private static final int FLAGS_NONE = 0;
    private static final int FLAGS_SILENT = 2;
    private static final ComMethod GET_BUFFER = ComMethod.of(GET_BUFFER_SLOT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS);
    private static final ComMethod RELEASE_BUFFER = ComMethod.of(RELEASE_BUFFER_SLOT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT);

    private final ComObject object;
    private final MemorySegment output;

    RenderClient(ComObject object, Arena arena) {
        this.object = object;
        this.output = arena.allocate(ValueLayout.ADDRESS);
    }

    void write(byte[] data, int frames, int bytes) {
        Hresult.check("GetBuffer", GET_BUFFER.invoke(object, frames, output));
        MemorySegment target = output.get(ValueLayout.ADDRESS, 0).reinterpret(bytes);
        MemorySegment.copy(MemorySegment.ofArray(data), 0, target, 0, bytes);
        Hresult.check("ReleaseBuffer", RELEASE_BUFFER.invoke(object, frames, FLAGS_NONE));
    }

    void writeSilence(int frames) {
        Hresult.check("GetBuffer", GET_BUFFER.invoke(object, frames, output));
        Hresult.check("ReleaseBuffer", RELEASE_BUFFER.invoke(object, frames, FLAGS_SILENT));
    }

    void release() {
        object.release();
    }
}
