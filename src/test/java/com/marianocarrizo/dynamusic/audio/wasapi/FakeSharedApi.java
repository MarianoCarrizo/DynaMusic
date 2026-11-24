package com.marianocarrizo.dynamusic.audio.wasapi;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;

final class FakeSharedApi implements WasapiSharedApi {

    private final int bufferFrames;
    private final int framesPerEvent;
    private final List<FakeSharedStream> streams = new ArrayList<>();
    private boolean supported = true;
    private RuntimeException openFailure;

    FakeSharedApi(int bufferFrames, int framesPerEvent) {
        this.bufferFrames = bufferFrames;
        this.framesPerEvent = framesPerEvent;
    }

    void supports(boolean supported) {
        this.supported = supported;
    }

    void failOpensWith(RuntimeException failure) {
        openFailure = failure;
    }

    int opens() {
        return streams.size();
    }

    FakeSharedStream stream() {
        return streams.get(streams.size() - 1);
    }

    @Override
    public boolean supportsShared(AudioFormat format) {
        return supported;
    }

    @Override
    public SharedStream openShared(AudioFormat format) {
        if (openFailure != null) throw openFailure;
        FakeSharedStream stream = new FakeSharedStream(bufferFrames, framesPerEvent);
        streams.add(stream);
        return stream;
    }
}
