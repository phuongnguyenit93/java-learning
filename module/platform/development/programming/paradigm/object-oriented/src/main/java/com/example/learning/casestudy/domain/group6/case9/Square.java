package com.example.learning.casestudy.domain.group6.case9;

import com.example.learning.casestudy.port.group6.case9.Shape;

public record Square(int side) implements Shape {
    public String draw() { return "square:" + side; }
}
