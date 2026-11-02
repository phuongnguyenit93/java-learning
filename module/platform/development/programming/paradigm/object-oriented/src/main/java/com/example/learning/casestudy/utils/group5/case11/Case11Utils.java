package com.example.learning.casestudy.utils.group5.case11;

import com.example.learning.casestudy.port.group5.case11.Shape;

public final class Case11Utils {
    public static long sumAreas(Shape... shapes) {
        long total = 0;
        for (Shape shape : shapes) total = Math.addExact(total, shape.area());
        return total;
    }
}
