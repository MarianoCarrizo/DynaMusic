package com.marianocarrizo.dynamusic.audio.wasapi;

import java.lang.foreign.Arena;

final class Endpoint implements AutoCloseable {

    private final ComThread com;
    private final Arena arena;
    private final DeviceEnumerator enumerator;
    private final AudioDevice device;

    private Endpoint(ComThread com, Arena arena, DeviceEnumerator enumerator, AudioDevice device) {
        this.com = com;
        this.arena = arena;
        this.enumerator = enumerator;
        this.device = device;
    }

    static Endpoint open() {
        ComThread com = ComThread.enter();
        Arena arena = Arena.ofConfined();
        DeviceEnumerator enumerator = null;
        try {
            enumerator = DeviceEnumerator.create(arena);
            AudioDevice device = enumerator.defaultRenderDevice(arena);
            return new Endpoint(com, arena, enumerator, device);
        } catch (RuntimeException failure) {
            if (enumerator != null) enumerator.release();
            arena.close();
            com.close();
            throw failure;
        }
    }

    Arena arena() {
        return arena;
    }

    AudioClient activateClient() {
        return device.activateClient(arena);
    }

    @Override
    public void close() {
        device.release();
        enumerator.release();
        arena.close();
        com.close();
    }
}
