package com.marianocarrizo.dynamusic.service.playback;

interface StreamListener {

    void positionChanged(double seconds, int generation);

    void ended(int generation);

    void failed(Throwable failure);
}
