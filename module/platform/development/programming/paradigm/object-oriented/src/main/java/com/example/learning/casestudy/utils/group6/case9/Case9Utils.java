package com.example.learning.casestudy.utils.group6.case9;

import com.example.learning.casestudy.port.group6.case9.Shape;
import java.util.List;

public final class Case9Utils {
    private Case9Utils() {}

    public static String render(List<? extends Shape> shapes) {
        return String.join("|", shapes.stream().map(Shape::draw).toList());
    }
}
