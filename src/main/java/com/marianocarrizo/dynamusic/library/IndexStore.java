package com.marianocarrizo.dynamusic.library;

import java.util.Collection;
import java.util.Map;

public interface IndexStore {

    Map<String, TrackEntry> load();

    void save(Collection<TrackEntry> entries);
}
