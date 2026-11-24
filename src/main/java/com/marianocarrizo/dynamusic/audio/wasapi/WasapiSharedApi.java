package com.marianocarrizo.dynamusic.audio.wasapi;

import javax.sound.sampled.AudioFormat;

public interface WasapiSharedApi {

    boolean supportsShared(AudioFormat format);

    SharedStream openShared(AudioFormat format);
}
