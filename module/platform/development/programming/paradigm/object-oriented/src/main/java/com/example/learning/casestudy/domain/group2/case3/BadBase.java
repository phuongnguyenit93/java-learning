package com.example.learning.casestudy.domain.group2.case3;

public class BadBase {
    public final String observed;
    public BadBase() {
        observed = token();
    }
    public String token() {
        return "base";
    }
}
