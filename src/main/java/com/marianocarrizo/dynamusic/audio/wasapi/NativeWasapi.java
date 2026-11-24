package com.marianocarrizo.dynamusic.audio.wasapi;

import com.marianocarrizo.dynamusic.audio.stream.OutputUnavailableException;
import java.util.List;
import javax.sound.sampled.AudioFormat;

public final class NativeWasapi implements WasapiApi {

    private static final int MIN_CHANNELS = 1;
    private static final int MAX_CHANNELS = 2;

    @Override
    public ExclusiveSupport probe(AudioFormat format) {
        List<SampleContainer> candidates = SampleContainer.candidates(format.getSampleSizeInBits());
        boolean channelsSupported = format.getChannels() >= MIN_CHANNELS && format.getChannels() <= MAX_CHANNELS;
        if (candidates.isEmpty() || !channelsSupported) return ExclusiveSupport.UNSUPPORTED;
        try (Endpoint endpoint = Endpoint.open()) {
            AudioClient client = endpoint.activateClient();
            try {
                return support(endpoint, client, format, candidates);
            } finally {
                client.release();
            }
        } catch (OutputUnavailableException unavailable) {
            return ExclusiveSupport.UNAVAILABLE;
        }
    }

    @Override
    public ExclusiveStream open(AudioFormat format, long periodHns) {
        return NativeExclusiveStream.open(format, periodHns);
    }

    private static ExclusiveSupport support(Endpoint endpoint, AudioClient client, AudioFormat format,
            List<SampleContainer> candidates) {
        int rate = Math.round(format.getSampleRate());
        for (SampleContainer candidate : candidates) {
            int code = client.isExclusiveSupported(WaveFormat.create(endpoint.arena(), candidate, rate,
                    format.getChannels()));
            if (code == 0) return ExclusiveSupport.SUPPORTED;
            ExclusiveSupport failure = ExclusiveSupport.ofFailure(code);
            if (failure != ExclusiveSupport.UNSUPPORTED) return failure;
        }
        return ExclusiveSupport.UNSUPPORTED;
    }
}
