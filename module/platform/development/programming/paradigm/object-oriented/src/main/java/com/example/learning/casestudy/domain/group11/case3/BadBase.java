package com.example.learning.casestudy.domain.group11.case3;

import java.util.Objects;

public class BadBase {
    final String id;
    public BadBase(String id) {
        this.id = id;
    }
    @Override
    public boolean equals(Object other) {
        return other instanceof BadBase b && Objects.equals(id, b.id);
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
