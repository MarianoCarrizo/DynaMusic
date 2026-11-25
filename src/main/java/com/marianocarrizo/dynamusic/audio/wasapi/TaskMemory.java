package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class TaskMemory {

    private static final MethodHandle FREE = Win32Library.OLE32.bind("CoTaskMemFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private TaskMemory() {
    }

    static void free(MemorySegment block) {
        NativeCall.invoke(FREE, block);
    }
}
