package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

final class ProAudioTask {

    private static final String TASK_NAME = "Pro Audio";
    private static final int WIDE_TERMINATOR_BYTES = 2;
    private static final MethodHandle ENTER = Win32Library.AVRT.bind("AvSetMmThreadCharacteristicsW",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle LEAVE = Win32Library.AVRT.bind("AvRevertMmThreadCharacteristics",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private final MemorySegment handle;
    private boolean left;

    private ProAudioTask(MemorySegment handle) {
        this.handle = handle;
    }

    static ProAudioTask enter(Arena arena) {
        int nameBytes = TASK_NAME.length() * WIDE_TERMINATOR_BYTES + WIDE_TERMINATOR_BYTES;
        MemorySegment name = arena.allocate(nameBytes, WIDE_TERMINATOR_BYTES);
        name.setString(0, TASK_NAME, StandardCharsets.UTF_16LE);
        MemorySegment index = arena.allocate(Integer.BYTES, Integer.BYTES);
        return new ProAudioTask(NativeCall.pointer(ENTER, name, index));
    }

    boolean active() {
        return handle.address() != 0;
    }

    void leave() {
        if (left || !active()) return;
        left = true;
        NativeCall.status(LEAVE, handle);
    }
}
