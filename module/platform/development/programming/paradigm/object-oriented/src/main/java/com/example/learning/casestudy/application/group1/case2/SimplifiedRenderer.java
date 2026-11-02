package com.example.learning.casestudy.application.group1.case2;

import com.example.learning.casestudy.model.group1.case2.Order2;

public final class SimplifiedRenderer {
    public String render(Order2 o) {
        if (o == null || o.id() == null || o.id().isBlank()
            || o.amount() < 0)
            throw new IllegalArgumentException("invalid order");
        return o.id() + ":" + o.amount();
    }
}
