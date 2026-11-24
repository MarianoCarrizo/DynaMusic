package com.marianocarrizo.dynamusic.service.playback;

import com.marianocarrizo.dynamusic.audio.stream.OutputStatus;

interface StreamListener {

    void positionChanged(double seconds, int generation);

    void ended(int generation);

    void failed(Throwable failure);

    void outputChanged(OutputStatus status);
}
