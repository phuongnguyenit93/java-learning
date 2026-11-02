package com.example.learning.casestudy.domain.group11.case5;

import java.util.List;
import java.util.Objects;

public record OrderSnapshot(List<LineView> lines) {
    public OrderSnapshot {
        lines = List.copyOf(Objects.requireNonNull(lines));
    }

}
