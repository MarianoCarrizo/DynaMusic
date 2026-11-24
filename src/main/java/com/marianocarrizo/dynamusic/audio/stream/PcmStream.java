package com.marianocarrizo.dynamusic.audio.stream;

public record PcmStream(PcmSource source, PcmSink sink) {

    public void close() {
        source.close();
        sink.close();
    }
}
