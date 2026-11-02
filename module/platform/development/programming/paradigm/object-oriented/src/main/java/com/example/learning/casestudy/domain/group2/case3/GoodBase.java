package com.example.learning.casestudy.domain.group2.case3;

public class GoodBase {
    final String id;
    public GoodBase(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
        this.id = id;
    }
    final String id() {
        return id;
    }
}
