package com.example.learning.casestudy.domain.group2.case3;

public final class BadChild extends BadBase {
    private String childToken = "ready";
    @Override public String token() {
        return childToken;
    }
}
