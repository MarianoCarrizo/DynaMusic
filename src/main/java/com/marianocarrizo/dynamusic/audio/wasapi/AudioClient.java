package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class AudioClient {

    private static final Guid RENDER_CLIENT_IID = new Guid("F294ACFC-3146-4483-A7BF-ADDCA7C260E2");
    private static final int SHARE_MODE_SHARED = 0;
    private static final int SHARE_MODE_EXCLUSIVE = 1;
    private static final int STREAMFLAGS_EVENTCALLBACK = 0x00040000;
    private static final int STREAMFLAGS_SRC_DEFAULT_QUALITY = 0x08000000;
    private static final int STREAMFLAGS_AUTOCONVERTPCM = 0x80000000;
    private static final long SHARED_BUFFER_HNS = 1_000_000L;
    private static final int SHARED_FLAGS = STREAMFLAGS_EVENTCALLBACK | STREAMFLAGS_AUTOCONVERTPCM
            | STREAMFLAGS_SRC_DEFAULT_QUALITY;
    private static final int INITIALIZE_SLOT = 3;
    private static final int GET_BUFFER_SIZE_SLOT = 4;
    private static final int GET_CURRENT_PADDING_SLOT = 6;
    private static final int IS_FORMAT_SUPPORTED_SLOT = 7;
    private static final int GET_MIX_FORMAT_SLOT = 8;
    private static final int GET_DEVICE_PERIOD_SLOT = 9;
    private static final int START_SLOT = 10;
    private static final int STOP_SLOT = 11;
    private static final int RESET_SLOT = 12;
    private static final int SET_EVENT_HANDLE_SLOT = 13;
    private static final int GET_SERVICE_SLOT = 14;
    private static final ComMethod INITIALIZE = ComMethod.of(INITIALIZE_SLOT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);
    private static final ComMethod GET_BUFFER_SIZE = ComMethod.of(GET_BUFFER_SIZE_SLOT, ValueLayout.ADDRESS);
    private static final ComMethod GET_CURRENT_PADDING = ComMethod.of(GET_CURRENT_PADDING_SLOT, ValueLayout.ADDRESS);
    private static final ComMethod IS_FORMAT_SUPPORTED = ComMethod.of(IS_FORMAT_SUPPORTED_SLOT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final ComMethod GET_MIX_FORMAT = ComMethod.of(GET_MIX_FORMAT_SLOT, ValueLayout.ADDRESS);
    private static final MethodHandle FREE_TASK_MEMORY = Win32Library.OLE32.bind("CoTaskMemFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final ComMethod GET_DEVICE_PERIOD = ComMethod.of(GET_DEVICE_PERIOD_SLOT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);
    private static final ComMethod START = ComMethod.of(START_SLOT);
    private static final ComMethod STOP = ComMethod.of(STOP_SLOT);
    private static final ComMethod RESET = ComMethod.of(RESET_SLOT);
    private static final ComMethod SET_EVENT_HANDLE = ComMethod.of(SET_EVENT_HANDLE_SLOT, ValueLayout.ADDRESS);
    private static final ComMethod GET_SERVICE = ComMethod.of(GET_SERVICE_SLOT, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);

    private final ComObject object;
    private final Arena arena;
    private final MemorySegment scratch;

    AudioClient(ComObject object, Arena arena) {
        this.object = object;
        this.arena = arena;
        this.scratch = arena.allocate(2L * Long.BYTES, Long.BYTES);
    }

    int initializeExclusive(long periodHns, MemorySegment format) {
        return INITIALIZE.invoke(object, SHARE_MODE_EXCLUSIVE, STREAMFLAGS_EVENTCALLBACK, periodHns, periodHns,
                format, MemorySegment.NULL);
    }

    int initializeShared(MemorySegment format) {
        return INITIALIZE.invoke(object, SHARE_MODE_SHARED, SHARED_FLAGS, SHARED_BUFFER_HNS, 0L, format, MemorySegment.NULL);
    }

    int mixChannels() {
        MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
        Hresult.check("GetMixFormat", GET_MIX_FORMAT.invoke(object, output));
        MemorySegment mix = output.get(ValueLayout.ADDRESS, 0);
        try {
            MemorySegment header = mix.reinterpret(WaveFormat.CHANNELS_OFFSET + Short.BYTES);
            return header.get(ValueLayout.JAVA_SHORT_UNALIGNED, WaveFormat.CHANNELS_OFFSET);
        } finally {
            NativeCall.invoke(FREE_TASK_MEMORY, mix);
        }
    }

    int isExclusiveSupported(MemorySegment format) {
        return IS_FORMAT_SUPPORTED.invoke(object, SHARE_MODE_EXCLUSIVE, format, MemorySegment.NULL);
    }

    long minimumPeriodHns() {
        Hresult.check("GetDevicePeriod", GET_DEVICE_PERIOD.invoke(object, scratch, scratch.asSlice(Long.BYTES)));
        return scratch.get(ValueLayout.JAVA_LONG, Long.BYTES);
    }

    int bufferFrames() {
        Hresult.check("GetBufferSize", GET_BUFFER_SIZE.invoke(object, scratch));
        return scratch.get(ValueLayout.JAVA_INT, 0);
    }

    int padding() {
        Hresult.check("GetCurrentPadding", GET_CURRENT_PADDING.invoke(object, scratch));
        return scratch.get(ValueLayout.JAVA_INT, 0);
    }

    void setEventHandle(Win32Event event) {
        Hresult.check("SetEventHandle", SET_EVENT_HANDLE.invoke(object, event.handle()));
    }

    RenderClient renderClient() {
        MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
        Hresult.check("GetService(IAudioRenderClient)",
                GET_SERVICE.invoke(object, RENDER_CLIENT_IID.allocate(arena), output));
        return new RenderClient(ComObject.fromOutput(output), arena);
    }

    void start() {
        Hresult.check("Start", START.invoke(object));
    }

    void stop() {
        Hresult.check("Stop", STOP.invoke(object));
    }

    void reset() {
        Hresult.check("Reset", RESET.invoke(object));
    }

    void release() {
        object.release();
    }
}
