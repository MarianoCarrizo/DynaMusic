package com.marianocarrizo.dynamusic.tags;

import java.io.IOException;

interface TagReader {

    void read(BoundedReader in, TagAccumulator out) throws IOException;
}
