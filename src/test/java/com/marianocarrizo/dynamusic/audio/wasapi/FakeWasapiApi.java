package com.marianocarrizo.dynamusic.audio.wasapi;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;

final class FakeWasapiApi implements WasapiApi {

    private final SampleContainer container;
    private final int periodFrames;
    private final List<FakeExclusiveStream> streams = new ArrayList<>();
    private ExclusiveSupport support = ExclusiveSupport.SUPPORTED;
    private RuntimeException openFailure;

    FakeWasapiApi(SampleContainer container, int periodFrames) {
        this.container = container;
        this.periodFrames = periodFrames;
    }

    void supports(ExclusiveSupport support) {
        this.support = support;
    }

    void failOpensWith(RuntimeException failure) {
        openFailure = failure;
    }

    void recoverOpens() {
        openFailure = null;
    }

    int opens() {
        return streams.size();
    }

    FakeExclusiveStream stream() {
        return streams.get(streams.size() - 1);
    }

    @Override
    public ExclusiveSupport probe(AudioFormat format) {
        return support;
    }

    @Override
    public ExclusiveStream open(AudioFormat format, long periodHns) {
        if (openFailure != null) throw openFailure;
        FakeExclusiveStream stream = new FakeExclusiveStream(container, periodFrames, format.getChannels());
        streams.add(stream);
        return stream;
    }
}
