package com.example.learning.casestudy.domain.group5.case9;

public class Normalizer {
    public final String process(String raw) {
        return "[" + normalize(raw) + "]";
    }
    protected String normalize(String raw) {
        return raw;
    }
}
