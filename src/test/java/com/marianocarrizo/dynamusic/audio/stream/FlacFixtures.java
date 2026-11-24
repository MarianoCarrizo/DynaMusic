package com.marianocarrizo.dynamusic.audio.stream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

final class FlacFixtures {

    static final int BLOCK_SIZE = 1024;
    static final int TOTAL_FRAMES = 17640;

    private FlacFixtures() {
    }

    static File file(int bits, boolean withSeekTable) {
        String name = "flac-" + bits + "bit-" + (withSeekTable ? "seektable" : "noseek") + ".flac";
        return Paths.get("src", "test", "resources", "audio", name).toFile();
    }

    static byte[] readAll(PcmSource source) throws IOException {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count = source.read(buffer);
        while (count != PcmSource.END_OF_STREAM) {
            collected.write(buffer, 0, count);
            count = source.read(buffer);
        }
        return collected.toByteArray();
    }
}
