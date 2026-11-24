package com.marianocarrizo.dynamusic.library;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface FileWalker {

    void walk(Path root, Consumer<FileStat> sink, BooleanSupplier cancelled);
}
