package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class Win32Event {

    private static final int WAIT_OBJECT_0 = 0;
    private static final int WAIT_TIMEOUT = 0x102;
    private static final MethodHandle CREATE = Win32Library.KERNEL32.bind("CreateEventW",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle WAIT = Win32Library.KERNEL32.bind("WaitForSingleObject",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CLOSE = Win32Library.KERNEL32.bind("CloseHandle",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private final MemorySegment handle;
    private boolean closed;

    private Win32Event(MemorySegment handle) {
        this.handle = handle;
    }

    static Win32Event create() {
        MemorySegment handle = NativeCall.pointer(CREATE, MemorySegment.NULL, 0, 0, MemorySegment.NULL);
        if (handle.address() == 0) throw new OutputUnavailableException("CreateEventW failed");
        return new Win32Event(handle);
    }

    MemorySegment handle() {
        return handle;
    }

    boolean await(int timeoutMillis) {
        int result = NativeCall.status(WAIT, handle, timeoutMillis);
        if (result == WAIT_OBJECT_0) return true;
        if (result == WAIT_TIMEOUT) return false;
        throw new OutputUnavailableException(String.format("WaitForSingleObject returned 0x%08X", result));
    }

    void close() {
        if (closed) return;
        closed = true;
        NativeCall.status(CLOSE, handle);
    }
}
