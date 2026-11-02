package com.example.learning.casestudy.domain.group5.case11;

import com.example.learning.casestudy.port.group5.case11.Shape;

public record Rectangle(long width, long height) implements Shape {
    public Rectangle {
        if (width < 0 || height < 0) throw new IllegalArgumentException("dimension");
    }
    public long area() {
        return Math.multiplyExact(width, height);
    }
}
