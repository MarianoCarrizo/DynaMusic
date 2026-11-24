package com.marianocarrizo.dynamusic.library;

public record IndexProgress(int filesSeen, int tagsRead, boolean done) {

    public static final IndexProgress IDLE = new IndexProgress(0, 0, true);
}
