package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class ComThread implements AutoCloseable {

    private static final int COINIT_MULTITHREADED = 0;
    private static final MethodHandle INITIALIZE = Win32Library.OLE32.bind("CoInitializeEx",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle UNINITIALIZE = Win32Library.OLE32.bind("CoUninitialize",
            FunctionDescriptor.ofVoid());

    private boolean open = true;

    private ComThread() {
    }

    static ComThread enter() {
        Hresult.check("CoInitializeEx", NativeCall.status(INITIALIZE, MemorySegment.NULL, COINIT_MULTITHREADED));
        return new ComThread();
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        NativeCall.invoke(UNINITIALIZE);
    }
}
