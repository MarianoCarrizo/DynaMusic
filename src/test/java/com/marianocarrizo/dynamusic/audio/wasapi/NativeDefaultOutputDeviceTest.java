package com.marianocarrizo.dynamusic.audio.wasapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.marianocarrizo.dynamusic.audio.stream.DefaultOutputDevice;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("wasapi")
class NativeDefaultOutputDeviceTest {

    @Test
    void readsTheSameIdTwiceWhenNothingChanges() {
        DefaultOutputDevice device = NativeDefaultOutputDevice.system();
        Optional<String> first = device.currentId();
        assumeTrue(first.isPresent());
        assertFalse(first.get().isBlank());
        assertEquals(first, device.currentId());
    }
}
