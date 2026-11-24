package com.marianocarrizo.dynamusic.library;

public record IndexProgress(int filesSeen, int tagsRead, boolean done, boolean failed) {

    public static final IndexProgress IDLE = new IndexProgress(0, 0, true);
    public static final IndexProgress FAILED = new IndexProgress(0, 0, true, true);

    public IndexProgress(int filesSeen, int tagsRead, boolean done) {
        this(filesSeen, tagsRead, done, false);
    }
}
