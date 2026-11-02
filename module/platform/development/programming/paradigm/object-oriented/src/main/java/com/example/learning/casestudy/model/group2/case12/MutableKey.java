package com.example.learning.casestudy.model.group2.case12;

public final class MutableKey {
    public String id;
    public MutableKey(String id) {
        this.id = id;
    }
    @Override public int hashCode() {
        return id.hashCode();
    }
    @Override public boolean equals(Object x) {
        return x instanceof MutableKey k && id.equals(k.id);
    }
}
