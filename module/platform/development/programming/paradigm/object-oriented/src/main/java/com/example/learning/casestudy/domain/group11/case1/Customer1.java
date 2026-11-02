package com.example.learning.casestudy.domain.group11.case1;

import java.util.Objects;

public final class Customer1 {
    private final String id;
    private String displayName;
    public Customer1(String id, String name) {
        this.id = Objects.requireNonNull(id);
        displayName = name;
    }
    public void rename(String name) {
        displayName = name;
    }
    @Override
    public boolean equals(Object other) {
        return other instanceof Customer1 c && id.equals(c.id);
    }
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
