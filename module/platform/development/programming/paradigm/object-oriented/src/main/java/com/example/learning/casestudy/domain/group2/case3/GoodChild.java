package com.example.learning.casestudy.domain.group2.case3;

public final class GoodChild extends GoodBase {
    private final String childToken = "ready";
    public GoodChild(String id) {
        super(id);
    }
    public String tokenAfterConstruction() {
        return id() + ":" + childToken;
    }
}
