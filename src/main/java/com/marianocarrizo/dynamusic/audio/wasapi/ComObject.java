package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class ComObject {

    private static final int RELEASE_SLOT = 2;
    private static final int POINTER_BYTES = 8;
    private static final int VTABLE_SLOTS = 64;
    private static final ComMethod RELEASE = ComMethod.of(RELEASE_SLOT);

    private final MemorySegment pointer;
    private boolean released;

    ComObject(MemorySegment interfacePointer) {
        this.pointer = interfacePointer.reinterpret(POINTER_BYTES);
    }

    static ComObject fromOutput(MemorySegment output) {
        return new ComObject(output.get(ValueLayout.ADDRESS, 0));
    }

    MemorySegment pointer() {
        return pointer;
    }

    MemorySegment method(int slot) {
        MemorySegment table = pointer.get(ValueLayout.ADDRESS, 0).reinterpret((long) POINTER_BYTES * VTABLE_SLOTS);
        return table.getAtIndex(ValueLayout.ADDRESS, slot);
    }

    void release() {
        if (released) return;
        released = true;
        try {
            RELEASE.invoke(this);
        } catch (RuntimeException ignored) {
            return;
        }
    }
}
