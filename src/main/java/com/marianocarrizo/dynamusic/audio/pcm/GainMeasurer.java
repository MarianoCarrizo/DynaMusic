package com.marianocarrizo.dynamusic.audio.pcm;

import java.io.File;
import java.io.IOException;
import java.util.OptionalDouble;

public interface GainMeasurer {

    double ofWav(File wav) throws IOException;

    OptionalDouble ofNative(File source) throws InterruptedException;
}
