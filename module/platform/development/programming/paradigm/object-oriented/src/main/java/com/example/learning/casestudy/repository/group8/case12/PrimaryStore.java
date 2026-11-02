package com.example.learning.casestudy.repository.group8.case12;

import com.example.learning.casestudy.port.group8.case12.Reindexable;
import com.example.learning.casestudy.port.group8.case12.Searchable;
import com.example.learning.casestudy.port.group8.case12.Writable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PrimaryStore implements Searchable, Writable, Reindexable {
    private final Map<String,String> data = new HashMap<>();
    private int rebuilds;
    public Optional<String> find(String key) { return Optional.ofNullable(data.get(key)); }
    public void save(String key, String value) { data.put(key, value); }
    public void rebuildIndex() { rebuilds++; }
    public int rebuildCount() { return rebuilds; }
}
