package com.example.learning.casestudy.domain.group11.case3;

public final class BadSubtype extends BadBase {
    public BadSubtype(String id) {
        super(id);
    }
    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == BadSubtype.class
        && super.equals(other);
    }
}
