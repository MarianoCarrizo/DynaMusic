package com.marianocarrizo.dynamusic.audio.pcm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public interface PcmConverter {

    void convert(File source, Path targetWav) throws IOException, InterruptedException;
}
