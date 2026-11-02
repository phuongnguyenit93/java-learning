package com.example.learning.casestudy.domain.group1.case7;

import com.example.learning.casestudy.domain.group1.case7.Status7;

public final class Payment {
    private final String id;
    private Status7 status = Status7.NEW;
    public Payment(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
        this.id = id;
    }
    public void confirm() { status = Status7.PAID; }
    public String id() { return id; }
    public Status7 status() { return status; }
}
