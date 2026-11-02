package com.example.learning.casestudy.domain.group6.case9;

import com.example.learning.casestudy.port.group6.case9.Shape;

public record Circle(int radius) implements Shape {
    public String draw() { return "circle:" + radius; }
}
