package com.example.learning.casestudy.adapter.group4.case9;

import com.example.learning.casestudy.port.group4.case9.Calculator;

public final class B9 implements Calculator {
    public int evaluate(Integer input) {
        if (input == null) throw new IllegalArgumentException("input");
        return Math.addExact(input, 1);
    }
}
