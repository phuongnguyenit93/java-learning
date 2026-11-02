package com.example.learning.casestudy.domain.group5.case8;

public class Base8 {
    private String code = "initial";
    public final String code() {
        return code;
    }
    protected final void rename(String newer) {
        code = newer;
    }
}
