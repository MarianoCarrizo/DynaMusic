package com.marianocarrizo.dynamusic.library;

import java.nio.file.Path;

public record FileStat(Path path, long size, long modified) {
}
