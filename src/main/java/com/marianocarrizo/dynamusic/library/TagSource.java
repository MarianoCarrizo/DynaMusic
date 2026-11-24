package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;

import java.nio.file.Path;

public interface TagSource {

    TrackTags read(Path file);
}
