package com.marianocarrizo.dynamusic.audio.stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

public interface SharedLines {

    boolean supports(AudioFormat format);

    PcmSink open(AudioFormat format) throws LineUnavailableException;

    static SharedLines system() {
        return new SharedLines() {
            @Override
            public boolean supports(AudioFormat format) {
                return LinePcmSink.supports(format);
            }

            @Override
            public PcmSink open(AudioFormat format) throws LineUnavailableException {
                return LinePcmSink.open(format);
            }
        };
    }
}
