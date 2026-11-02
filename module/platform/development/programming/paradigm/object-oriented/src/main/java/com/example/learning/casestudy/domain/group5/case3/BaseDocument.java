package com.example.learning.casestudy.domain.group5.case3;

import java.util.Objects;

public abstract class BaseDocument {
    private final String id;
    public BaseDocument(String id) {
        this.id = Objects.requireNonNull(id);
        // KHÔNG gọi validate() / render() có thể override tại đây.
    }
    public String id() {
        return id;
    }
}
