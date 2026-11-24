package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

enum Win32Library {
    OLE32("ole32"),
    KERNEL32("kernel32"),
    AVRT("avrt");

    private static final Linker LINKER = Linker.nativeLinker();

    private final String library;
    private SymbolLookup lookup;

    Win32Library(String library) {
        this.library = library;
    }

    MethodHandle bind(String function, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(symbols().find(function).orElseThrow(), descriptor);
    }

    private synchronized SymbolLookup symbols() {
        if (lookup == null) lookup = SymbolLookup.libraryLookup(library, Arena.global());
        return lookup;
    }
}
