package com.example.learning.casestudy.repository.group8.case6;

import com.example.learning.casestudy.port.group8.case6.ReadStore;
import com.example.learning.casestudy.port.group8.case6.WriteStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ReadWriteStore implements ReadStore, WriteStore {
    private final Map<String, String> values = new HashMap<>();
    public Optional<String> get(String id) { return Optional.ofNullable(values.get(id)); }
    public void put(String id, String value) { values.put(id, value); }
}
