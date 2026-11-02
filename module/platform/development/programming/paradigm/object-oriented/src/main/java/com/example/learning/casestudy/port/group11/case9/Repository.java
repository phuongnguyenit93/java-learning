package com.example.learning.casestudy.port.group11.case9;

import java.util.Optional;

public interface Repository<T, ID> {
    Optional<T> find(ID id);
    void save(ID id, T entity);
}
