package com.example.learning.casestudy.domain.group6.case10;

import com.example.learning.casestudy.port.group6.case10.Primary;
import com.example.learning.casestudy.port.group6.case10.Secondary;

public class Combined implements Primary, Secondary {
    @Override public String label() {
        return Primary.super.label() + "+" + Secondary.super.label();
    }
}
