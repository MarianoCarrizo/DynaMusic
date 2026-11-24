package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class ComMethod {

    private static final Linker LINKER = Linker.nativeLinker();

    private final int slot;
    private final MethodHandle handle;

    private ComMethod(int slot, MethodHandle handle) {
        this.slot = slot;
        this.handle = handle;
    }

    static ComMethod of(int slot, MemoryLayout... parameters) {
        MemoryLayout[] layouts = new MemoryLayout[parameters.length + 1];
        layouts[0] = ValueLayout.ADDRESS;
        System.arraycopy(parameters, 0, layouts, 1, parameters.length);
        return new ComMethod(slot, LINKER.downcallHandle(FunctionDescriptor.of(ValueLayout.JAVA_INT, layouts)));
    }

    int invoke(ComObject target, Object... arguments) {
        Object[] all = new Object[arguments.length + 2];
        all[0] = target.method(slot);
        all[1] = target.pointer();
        System.arraycopy(arguments, 0, all, 2, arguments.length);
        return (int) NativeCall.invoke(handle, all);
    }
}
