package com.example.learning.casestudy.utils.group1.case11;

import com.example.learning.casestudy.domain.group1.case11.Order11;
import com.example.learning.casestudy.dto.group1.case11.OrderDto;

public final class Case11Utils {
    private Case11Utils() {}

    public static OrderDto toDto(Order11 order) {
        return new OrderDto(order.id(), order.status().name());
    }

    public static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
