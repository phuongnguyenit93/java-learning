package com.example.learning.casestudy.repository.group11.case9;

import com.example.learning.casestudy.port.group11.case9.Repository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MemoryRepository<T, ID> implements Repository<T, ID> {
    public MemoryRepository() {
    }
    private final Map<ID, T> rows = new HashMap<>();
    public Optional<T> find(ID id) {
        return Optional.ofNullable(rows.get(id));
    }
    public void save(ID id, T entity) {
        rows.put(id, entity);
    }
}
