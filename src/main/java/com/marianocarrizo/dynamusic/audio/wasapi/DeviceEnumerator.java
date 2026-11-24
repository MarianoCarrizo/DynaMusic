package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class DeviceEnumerator {

    private static final Guid CLSID = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final Guid IID = new Guid("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static final int CLSCTX_ALL = 0x17;
    private static final int DATA_FLOW_RENDER = 0;
    private static final int ROLE_CONSOLE = 0;
    private static final int GET_DEFAULT_ENDPOINT_SLOT = 4;
    private static final MethodHandle CREATE_INSTANCE = Win32Library.OLE32.bind("CoCreateInstance",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final ComMethod GET_DEFAULT_ENDPOINT = ComMethod.of(GET_DEFAULT_ENDPOINT_SLOT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

    private final ComObject object;

    private DeviceEnumerator(ComObject object) {
        this.object = object;
    }

    static DeviceEnumerator create(Arena arena) {
        MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
        int code = NativeCall.status(CREATE_INSTANCE, CLSID.allocate(arena), MemorySegment.NULL, CLSCTX_ALL,
                IID.allocate(arena), output);
        Hresult.check("CoCreateInstance(MMDeviceEnumerator)", code);
        return new DeviceEnumerator(ComObject.fromOutput(output));
    }

    AudioDevice defaultRenderDevice(Arena arena) {
        MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
        Hresult.check("GetDefaultAudioEndpoint",
                GET_DEFAULT_ENDPOINT.invoke(object, DATA_FLOW_RENDER, ROLE_CONSOLE, output));
        return new AudioDevice(ComObject.fromOutput(output));
    }

    void release() {
        object.release();
    }
}
