package com.marianocarrizo.dynamusic.library;

import com.marianocarrizo.dynamusic.tags.TrackTags;

public record TrackEntry(String path, long size, long modified, TrackTags tags) {
}
