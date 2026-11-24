package com.marianocarrizo.dynamusic.audio.wasapi;

import javax.sound.sampled.AudioFormat;

public interface WasapiApi {

    ExclusiveSupport probe(AudioFormat format);

    ExclusiveStream open(AudioFormat format, long periodHns);
}
