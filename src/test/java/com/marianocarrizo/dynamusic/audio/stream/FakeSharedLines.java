package com.marianocarrizo.dynamusic.audio.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

public final class FakeSharedLines implements SharedLines {

    private final List<FakePcmSink> opened = new ArrayList<>();
    private Predicate<AudioFormat> supported = format -> true;
    private boolean unavailable;

    public void supportOnly(Predicate<AudioFormat> supported) {
        this.supported = supported;
    }

    public void becomeUnavailable() {
        unavailable = true;
    }

    public List<FakePcmSink> opened() {
        return opened;
    }

    public FakePcmSink last() {
        return opened.get(opened.size() - 1);
    }

    @Override
    public boolean supports(AudioFormat format) {
        return supported.test(format);
    }

    @Override
    public PcmSink open(AudioFormat format) throws LineUnavailableException {
        if (unavailable) throw new LineUnavailableException("no shared line");
        FakePcmSink sink = new FakePcmSink(format);
        opened.add(sink);
        return sink;
    }
}
