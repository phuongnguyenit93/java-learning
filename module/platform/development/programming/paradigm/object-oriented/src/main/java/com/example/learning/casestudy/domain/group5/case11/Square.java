package com.example.learning.casestudy.domain.group5.case11;

import com.example.learning.casestudy.port.group5.case11.Shape;

public record Square(long side) implements Shape {
    public Square {
        if (side < 0) throw new IllegalArgumentException("dimension");
    }
    public long area() {
        return Math.multiplyExact(side, side);
    }
}
