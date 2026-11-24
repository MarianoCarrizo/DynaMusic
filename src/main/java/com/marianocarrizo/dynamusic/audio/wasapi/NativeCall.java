package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

final class NativeCall {

    private NativeCall() {
    }

    static int status(MethodHandle handle, Object... arguments) {
        return (int) invoke(handle, arguments);
    }

    static MemorySegment pointer(MethodHandle handle, Object... arguments) {
        return (MemorySegment) invoke(handle, arguments);
    }

    static Object invoke(MethodHandle handle, Object... arguments) {
        try {
            return handle.invokeWithArguments(arguments);
        } catch (RuntimeException | Error unchanged) {
            throw unchanged;
        } catch (Throwable failure) {
            throw new OutputUnavailableException("Native call failed", failure);
        }
    }
}
